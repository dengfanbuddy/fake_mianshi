# ADR-0009: 本地语音 sidecar 的生命周期由后端托管

## 状态

已采纳（2026-10-20）

## 背景

ADR-0004 把 TTS 落地成本地 Qwen3-TTS sidecar（`voice-service/`，Python + uvicorn，监听 9100）。
落地后暴露出的实际使用问题不是音质，而是**运维**：

- sidecar 是独立进程，后端起停完全不碰它。用户重开机、关掉终端窗口后，页面上的语音就"用不了"，
  而页面上不会说清原因——`voice_config.provider` 明明已经是 `local_qwen`，`isActive=true`，
  配置看起来一切正常，只有 `/voice/local/health` 的 `reachable:false` 才知道服务没起。
- 反过来，光看配置也判断不了"该不该去启动它"：用户不知道这是个要手工跑的独立服务。

即：**把"一个必须常驻的外部依赖"以"可选的手工步骤"形式交付给用户，本身就是设计缺陷。**

## 决策

由后端（`LocalVoiceProcessManager`）托管 sidecar 进程的生命周期：

| 时机 | 行为 |
|---|---|
| 后端启动完成（`ApplicationReadyEvent`） | 若生效供应商是 `local_qwen` 且 sidecar 不可连通 → 拉起 |
| 后端关闭（`@PreDestroy`） | 结束**由自己拉起的** sidecar |
| 页面操作 | `POST /voice/local/start` / `POST /voice/local/stop`，`GET /voice/local/process` 看状态与日志 |

### 核心不变量：所有权

后端只结束 `owned=true` 的进程。启动时先探 `/health`：

- 可连通 → 记为**外部实例**，不接管、不结束（用户可能自己开着 `run.ps1` 在调模型）；
- 不可连通 → 由后端拉起，`owned=true`，此后由后端负责结束。

这条不变量防止"关掉后端把用户手工跑的服务一起杀了"这类更难排查的事故，已在
`LocalVoiceProcessManagerTest` 里用 4 个用例锁住。

### 具体实现取舍

- **不等待模型加载**：`pb.start()` 立即返回，加载约 15–20s 由前端轮询 `/local/health` 显示进度。
  若在后端启动线程里等加载完成，启动时间会被拉长 20s，且加载慢等于启动失败，语义不对。
- **必须持续读走子进程 stdout**：管道缓冲写满会阻塞 uvicorn——这是 spawn 子进程最经典的坑。
  用一个 daemon 线程把输出泵进 200 行环形缓冲，并在页面「最近日志」里展示，排错时不用去翻终端。
- **日志按"先 UTF-8 再 GBK"逐行解码**：sidecar 的输出编码取决于它的运行环境，不是我们的假设。
  实测中即使给子进程注入 `PYTHONIOENCODING=utf-8`，中文日志仍有部分以 GBK 字节写出；
  而用户在自己的 cp936 控制台里手工跑 `run.ps1` 时更是必然 GBK。因此不能直接套 UTF-8 的 Reader，
  改为读字节后严格试 UTF-8、失败退 GBK、再失败才用替换字符。
  （顺序不能反：GBK 解码几乎不报错，先试 GBK 会把 UTF-8 中文解成乱码。）
- **过滤加载进度刷屏**：transformers 的 `Loading weights: 42%|███▌ | 167/402` 会一次刷几十行，
  把真正有用的告警（SoX 缺失、CUDA graph 捕获失败）挤出可视区域，故直接丢弃这类行。
- **pid 文件用于崩溃后接管**：关闭钩子在硬杀（IDE 停止按钮、崩溃、断电）时不会执行，
  上次拉起的 Python 会变成占着 2.7GB 显存的孤儿，而所有权规则又保证它永不被清理。
  故拉起时把 PID 写进 `data/voice-sidecar.pid`；下次启动读到该 PID 且进程仍活着，
  就判定为"自己的遗留"并接管，让它能在下次正常关闭时被清理。
  pid 文件只由本后端写入，这是"可以安全接管"的依据；外部实例没有这个文件，仍然不碰。
- **测试环境强制关闭**：`src/test/resources/application.yml` 设 `app.voice.local.auto-start=false` 与
  独立的 `pid-file`，否则每个 `@SpringBootTest` 都会真的去 spawn Python。
- **只做单实例**：不做端口冲突探测/多实例池。sidecar 内部已串行化合成请求（模型非线程安全），
  多人并发场景按 ADR-0004 的说明另行多实例部署。
- **kill 用进程树**：先 `destroy()` 等待 `stop-timeout-seconds`（默认 15s），超时再 `destroyForcibly()`，
  子孙进程一并处理。实测 Windows 上 uvicorn 表现为"启动器 python + 真正监听 9100 的子进程"两个 PID，
  只杀父会留下占显存的子进程。

## 后果

**收益**

- 用户不再需要知道 sidecar 的存在：装了 `.venv` 和模型权重之后，用本地 TTS 只需在设置页选一下供应商。
- 后端关掉，GPU 显存随之释放，不会留下一个占 2.7GB 显存的孤儿进程。
- 排错路径变短：状态、PID、最近日志都在页面上。

**代价**

- 后端多了一份进程管理职责（spawn/kill/日志），这些代码天然难测——已用 mock 协作者的单元测试
  锁住所有权判定，真实 spawn/kill 靠手工端到端验证覆盖。
- 发布包里 `app.jar` 不带 Python 与模型权重，所以"自动拉起"只对**源码目录布局**成立
  （`backend/` 与 `voice-service/` 同级）。分发到别处需配 `app.voice.local.service-dir` 与
  `app.voice.local.python`。这是刻意留的配置口子，而不是自动猜路径。
