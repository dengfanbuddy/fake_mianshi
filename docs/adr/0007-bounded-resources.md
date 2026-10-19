# ADR-0007: 无界资源的收口 —— 会话缓存落库、线程池有界、列表分页与索引

**Date:** 2026-10-19
**Status:** Accepted

## 背景

这个项目的所有数据都在本地 SQLite + 单个 Spring Boot 进程里，早期为了快，四处用了"够用就行"的写法。
随着会话累积，四类**无界资源**逐渐成为定时炸弹：

1. **内存里的会话缓存**：`MockInterviewServiceImpl` 用两个 `ConcurrentHashMap`
   （`outlineCache`、`referenceWrittenTestFlags`）保存面试大纲与"是否引用笔试结果"。
   它们只在 `concludeInterview` 里清理——用户中途放弃（关页面、直接开新面试）的会话会**永久驻留**，
   每个会话一条几十 KB 的 JSON；而 `getOutline` 是 `computeIfAbsent`，任何进过面试的会话都会写入。
   更糟的是"不持久化"本身是个功能缺陷：**服务重启后大纲会被重新生成，同一场面试中途换题单**。

2. **线程池**：4 处 `Executors.newCachedThreadPool()`（出题 SSE、分析 SSE、后台分析、语音回复）。
   线程数无上限，任务提交即新建线程。单个 SSE 任务可占线程 2–7 分钟，语音回复还要串行等 LLM + 逐句 TTS；
   LLM 侧一卡或客户端反复重试，线程数会持续上涨（每线程约 1MB 栈），最终拖垮进程。

3. **列表查询**：`GET /project` 与 `GET /session/project/{id}` 一律全量返回；
   分页完全不存在。项目一多，首页一次性渲染全部卡片。

4. **没有索引**：`schema.sql` 除了主键和 `prompt_template` 的唯一约束，**一个索引都没有**。
   而代码里几乎所有查询都是 `WHERE session_id = ?` / `WHERE project_id = ?` + `ORDER BY ...`，
   每次请求都是全表扫。`GET /session/stats` 更是把**所有会话读进内存**再 `filter` 计数。

## 决策

### 1. 会话级数据落库，不再放内存

`interview_session` 增加 `outline TEXT` 与 `reference_written_test BOOLEAN` 两列：

- `start()` 生成大纲后随会话一起落库；
- `getOutline(session)` 直接读会话字段；为空（升级前创建的老会话）时才生成一次并**回写**；
- `buildWrittenTestContext` 改读 `session.getReferenceWrittenTest()`；
- 两个 `ConcurrentHashMap` 与其清理代码全部删除。

列通过两条路径补齐：`schema.sql` 的 `CREATE TABLE`（新库）+ `DatabaseMigration.ensureColumn`（旧库 ALTER TABLE）。

### 2. 线程池改有界，过载显式失败

新增 `config/AppExecutors.bounded(prefix, threads, queueCapacity)`：固定线程数 + 有界队列 + `AbortPolicy` + 命名守护线程。

| 用途 | 配置 | 过载行为 |
|---|---|---|
| 出题 SSE | 4 线程 / 8 队列 | 推 `event: error`「当前出题任务较多，请稍后重试」 |
| 分析 SSE | 4 线程 / 8 队列 | 推 `event: error`，前端退回轮询 |
| 后台分析 | 4 线程 / 16 队列 | 放弃本次自动触发（报告页入口会重新触发） |
| 语音回复 | 4 线程 / 16 队列 | WS 回 `error` 并复位 `replyStarted`，用户可重试 |

要点是**过载时给出可读的错误，而不是默默排队到超时或无限建线程**。

### 3. 项目列表分页

新增 `GET /project/page?page=&size=`，返回 `PageResult{records,total,page,size,totalPages}`；
`size` 上限 100、页码至少 1（`PageResult.normalize*`）。`Projects.vue` 加 `el-pagination`，每页 12 条。

**故意没做的**：会话列表（`GET /session/project/{id}`）不分页。`ProjectDetail.vue` 的
`inProgressSessions`、「完整流程」判断（`hasWritten`）都依赖**完整列表**，只分页会让这两处逻辑静默出错。
它的量级也远小于项目列表（单人多项目练习）。真要分页时，得先把这两个判断改成服务端查询。

### 4. 索引 + COUNT 统计

`schema.sql` 末尾补 12 个 `CREATE INDEX IF NOT EXISTS`（会话/消息/题目/答案/分析/弱点/简历/岗位/历史），
全部对齐代码里真实出现的 `WHERE`/`ORDER BY` 组合。因为 `spring.sql.init.mode=always`，
`schema.sql` 每次启动都会执行，旧库下次启动即自动补齐索引，不需要额外的迁移代码。

`GET /session/stats` 改为 `SELECT COUNT(*)`（`countAll` / `countByType`），不再把全部会话读进内存。

## 后果

**收益**

- 内存占用与会话数解耦：不再因为"用户开了很多面试没结束"而单调增长。
- 重启后继续同一场面试仍然用同一份大纲（连贯性修复，不只是省内存）。
- 过载可控：线程数、队列长度、分页条数都有确定上限，且失败原因对用户可见。
- 查询走索引，`stats` 变成一次聚合。

**代价与限制**

- 新增依赖列：`interview_session.outline`/`reference_written_test`，老库靠启动迁移补齐
  （`DatabaseMigration` 失败只 warn，不阻断启动——极端情况下会出现"缺列"错误，需重启或手工补）。
- 大纲落库后，会话行更大（几十 KB/场）；SQLite 单文件，长期使用需要定期清理旧会话。
- 有界线程池对**单用户**场景没有实际收益（4 线程足够），真正的收益是"不会失控"；
  若将来做多人并发，需要把线程数与队列做成配置项，而不是常量。
- 分页只覆盖项目列表；会话列表、消息列表仍是全量——
  消息列表是 LLM 上下文的必要输入，不适合分页，属于有意保留。

## 相关

- ADR-0005（LLM 调用在事务外）、ADR-0006（笔试计时由服务端裁决）
- 实现：`AppExecutors`、`MockInterviewServiceImpl`、`schema.sql`、`DatabaseMigration`、
  `PageResult`、`ProjectServiceImpl.findPage`、`SessionController.stats`、`Projects.vue`
