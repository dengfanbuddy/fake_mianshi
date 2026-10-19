# ADR-0005: LLM 调用一律在数据库事务之外，只有写库步骤进短事务

**Date:** 2026-10-19
**Status:** Accepted

## 背景

出题、面试对话、面试分析三类能力都要调用 LLM，单次调用耗时 30–120 秒（流式分析更久，
`SseEmitter` 超时设为 420 秒）。原实现把 `@Transactional` 直接标在"调用 LLM + 落库"的整个方法上：

- `QuestionGenerationServiceImpl.generateWrittenTestQuestions[Stream]`
- `AnalysisServiceImpl.analyzeSession` / `analyzeSessionStream` / `analyzeHistory` / `refreshHistory` / `refreshAnalysis`

后端用 SQLite（WAL + `busy_timeout=30000`）。这样一来：

1. **写锁被长期占住**：一个出题请求能锁住写连接一两分钟，期间其他写入（提交答案、保存配置、
   更新弱点标签）最多等 30 秒就抛 `SQLITE_BUSY`。
2. **连接池被拖住**：每个进行中的 LLM 调用都占着一个 Hikari 连接，并发几次就把池耗尽，
   连"读"请求也开始排队。
3. **流式场景尤其糟**：`analyzeSessionStream` 的事务要等整条 SSE 推完才提交，
   期间分析结果不可见，客户端断连还会把已生成的内容一起回滚掉。
4. **`refreshHistory` 先删后生成**：删掉旧快照再调 LLM，一旦 LLM 失败，历史分析直接丢失。

## 决策

**长外部调用与数据库事务解耦**：LLM（以及任何网络长耗时调用）在事务外执行，
只有真正写库的那几步用 `TransactionTemplate` 包成一个几毫秒的短事务。

- 出题：`saveQuestions(...)` 一批 insert 包在短事务里——12 道题必须整体成功，
  不允许出现"只存了 7 道"的残缺考试；
- 分析：`persistAnalysis(...)` 把 会话分析 + 单题分析 + 简答题评分回流 + 弱点标签
  四项写库包在同一个短事务里，避免留下"半份分析"被幂等逻辑当成完整结果永久返回；
  弱点标签更新用自调用加入同一事务（`updateWeaknessTags` 不需要自己的事务边界）；
- `refreshHistory`：**先生成、后删除**——LLM 成功后才在短事务里删旧快照并写新的，
  LLM 失败时旧快照保持原样；
- `refreshAnalysis` / `deleteAnalysis`：删除动作各自是一个短事务；
- 纯读方法（`getSessionAnalysis`、`analyzeQuestions`、`getHistoryStatus`）保留
  `@Transactional(readOnly = true)`，它们不涉及外部调用。

### 4. 收尾自动分析改为异步触发，报告页等待而不是报错

原来 `WrittenTestServiceImpl` / `MockInterviewServiceImpl` 的 `triggerAnalysisAfterCommit`
在 `afterCommit` 里**同步**调用 `analyzeSession`——它跑在请求线程上，交卷/结束面试的接口
要等整轮 LLM 分析（30–120 秒）才返回，用户点完"交卷"会干等一两分钟。

现在改为调用 `AnalysisService.triggerAnalyze`（`afterCommit` 里提交到后台线程池，立即返回）。
由此带来一个新问题：用户交卷后立刻点进报告页时，分析往往还在生成。因此
`AnalysisController.streamSession` 在检测到后台正在生成时**不再直接返回"分析正在生成中"错误**，
而是在 SSE 线程里等待（最多 240 秒，期间只发注释行探活），生成完成后推 `done`；
前端 8 秒无输出会自动切到轮询兜底，两条路都能拿到报告。

## 后果

**收益**

- 出题/分析期间数据库写入不再被阻塞，也不再占用连接池。
- 流式分析的结果在生成完成的那一刻一次性可见；客户端断连不会回滚已生成的报告。
- 历史分析刷新不再有"删完才发现 LLM 挂了"的窗口。
- 交卷 / 结束面试不再等待 LLM 分析，接口恢复秒级响应；报告页用 SSE 等待或轮询都能拿到结果。
- 写库阶段的原子性反而比原来更明确（原来靠外层大事务"顺带"保证）。

**代价与限制**

- 事务边界从"方法级注解"变成"代码内显式 `TransactionTemplate`"，阅读时要多看一层；
  新增写库步骤时必须记得放进 `persist*`/`save*` 里，否则就失去了原子性。
- 分析改为异步后，"分析已就绪"变成最终一致：报告页要能接受"生成中"的中间态
  （已由 SSE 等待 + 轮询兜底覆盖，但新增入口必须自己处理）。
- `TransactionTemplate` 通过构造器注入，纯 Mockito 单测需要传一个直通实现
  （见 `AnalysisServiceTest` / `QuestionGenerationServiceTest` 的 `passthroughTransactionTemplate()`）。
- 事务变短后，跨阶段的一致性只能靠"先生成后写入"的顺序保证，不能再依赖长事务回滚；
  因此所有生成阶段都必须幂等（现有 `analyzingSessions` 防重 + 已存在即返回）。
- 后台线程池仍是 `newCachedThreadPool()`（无上限），高并发下缺少背压；后续应换成有界池。

## 相关

- ADR-0002（自适应出题）、ADR-0006（笔试计时由服务端裁决）
- 实现：`QuestionGenerationServiceImpl`、`AnalysisServiceImpl`、`WrittenTestServiceImpl.discardSession`
