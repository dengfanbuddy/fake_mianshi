# ADR-0006: 笔试计时由服务端裁决，超时提交只打标记不清空作答

**Date:** 2026-10-19
**Status:** Accepted

## 背景

笔试是限时环节（默认 60 分钟，可配 10–180 分钟）。原实现的计时完全在前端：

- 前端拿 `startedAt` 自己算 `deadline = startedAt + timeLimit * 60000`，存在 `sessionStorage`；
- 到点由前端调 `POST /submit` 自动交卷；
- 后端 `submit` **只看会话状态是否为 IN_PROGRESS，完全不看时间**。

这带来三个问题：

1. **计时可被绕过**：改 `sessionStorage` 里的 `deadline`、或把系统时钟往回拨，
   就能无限延长考试时间，而后端照样给出满分。对自我训练产品来说这等于形同虚设。
2. **倒计时提前开跑**：`startedAt` 在创建会话时写入，而题目是随后调 LLM 生成的（几十秒）。
   用户还在看"AI 正在出题"的等待页，考试时间已经开始扣了。
3. **无痕迹**：即使有人拖到两小时后才交卷，报告里也看不出任何异常。

## 决策

### 1. 出题完成后才开表

`createSession` 只建会话（不带 `startedAt`）；LLM 出题成功后再由 `startClock` 写入 `startedAt` 并落库。
出题失败则 `discardSession` 删除该孤儿会话，不留下 IN_PROGRESS 的空考试。

### 2. 截止时间由服务端下发

`WrittenTestStartResponse` / `getExamInProgress` 增加 `deadline`（epoch 毫秒），
前端**只负责展示**，不再自行推导；仅当字段缺失时才退回本地计算。
这样本地时钟、缓存被改动都不会影响服务端判定。

### 3. 交卷时服务端校验，超时不清空作答

`submit` 用库里的 `startedAt + timeLimit` 判定，留 **30 秒宽限**（覆盖自动交卷的网络/渲染延迟），
超出即 `expired = true`，并写入警告日志；响应与详情接口都返回
`expired` / `elapsedSeconds` / `timeLimit`，报告页顶部展示"本次笔试为超时提交"。

超时**照常判分、照常入库**——不因超时销毁用户的作答。理由：

- 这是本地单人自我训练工具，不存在作弊收益转移给他人的问题，惩罚性丢弃答案只会伤害用户；
- 真正的损失场景（笔记本休眠、浏览器被切到后台）恰恰是用户最不该被清空数据的场景；
- 需要的只是"让分数不被误读"，一个显式标记就够了。

详情接口用**交卷时刻**（`completedAt`）而非当前时间判定，否则事后每看一次报告都会显示"超时"。

## 后果

**收益**

- 考试时长不可能被前端绕过；刷新/离开后用 `getExamInProgress` 恢复，剩余时间仍是真实剩余。
- 用户实际可作答时长 = 配置时长，不再被出题耗时吃掉。
- 超时提交在成绩页可见，用户自己知道这个分数该打多少折扣。

**代价与限制**

- 30 秒宽限是固定常量（`WrittenTestServiceImpl.SUBMIT_GRACE_SECONDS`），没有做成配置项；
  如果以后要给"网络特别差"的场景更宽容，需要提到配置里。
- 服务端只打标记不拒收，意味着"防作弊"强度是有上限的；若将来要走多人/考评场景，
  需要改成拒收 + 服务端定期快照作答（当前作答只存在于前端 sessionStorage，无服务端草稿）。
- 时钟仍依赖服务器本地时区（`ZoneId.systemDefault()`），跨时区部署需统一时区。

## 相关

- ADR-0005（LLM 调用在事务外）
- 实现：`WrittenTestServiceImpl`（`startClock` / `deadlineOf` / `isExpired` / `elapsedSecondsOf`）、
  `WrittenTestStartResponse`、`WrittenTestSubmitResponse`、`WrittenTestDetailResponse`、
  `frontend/src/views/written-test/WrittenTest{Start,Exam,Result}.vue`
