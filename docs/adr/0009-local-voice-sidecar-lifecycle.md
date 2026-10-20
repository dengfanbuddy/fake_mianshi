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

## 修订（2026-10-20，事故后）

**上面「后端关掉，GPU 显存随之释放，不会留下孤儿进程」当时并不成立。** 真实现象：后端已经停了，
`nvidia-smi` 仍显示 6.2GB 被占；`wmic` 看到两个 `python.exe`：

```
21008  .venv\Scripts\python.exe -m uvicorn app.main:app --port 9100      （父 = 后端 JVM）
36236  D:\MyApplication\Python312\python.exe -m uvicorn app.main:app --port 9100   （父 = 21008）
```

`.venv/Scripts/python.exe` 只是启动器，它再拉起 base 解释器去跑 uvicorn；
**真正吃显存的是后者**。原实现有三个洞，叠在一起就成了「后端停了、显存没降、用户无从下手」：

1. **成功判据错了**：只等 `root.onExit()`（启动器退出）。启动器秒退，代码直接 `return`，
   从没检查真正干活的子进程死没死——启动器已死而 worker 仍占 4GB 时，日志照样写「已结束」。
2. **接管来的句柄子孙列表不可靠**：`ProcessHandle.descendants()` 对不是本 JVM 创建的进程
   在 Windows 上可能给空集。崩溃重启后走的正是「接管」路径，于是只杀掉启动器。
3. **pid 文件丢了就永久失联**：孤儿只靠 pid 文件认领，文件一没（被清理、写失败），
   就再没有任何代码路径会去回收它。

### 修法：判据换成「端口是否关闭」

- `killOwned()` 以**端口 9100 关闭**为唯一成功判据；`stop()` 返回 `freed` 字段，
  没关掉就明说「端口仍在监听，显存可能未释放」，不再谎报成功。
- 强杀走 `taskkill /T /F`（Windows），沿操作系统父子链穿过「启动器 → 解释器」，
  不依赖 `ProcessHandle.descendants()`；目标 PID 同时包含启动器与 `/health` 自报的 `pid`。
- `/health` 新增 `pid` 与 `cwd`：`pid` 用于精确结束真正占显存的进程；`cwd` 用于在
  **pid 文件丢失后**判定「这个在监听 9100 的实例就是本项目的 voice-service」从而接管它。
  `cwd` 不匹配的一律当外部实例不碰——**所有权不变量没有放松**。
- 新增 `POST /voice/local/stop?force=true`：后端还活着时回收「未托管的孤儿」的直通车。
- 新增 `voice-service/stop.ps1`：**后端已经关掉**时的逃生口（按端口 + 命令行定位进程，
  `taskkill /T` 整棵树，最后验证端口是否真的释放）。

### 回归测试

- `stopKillsGrandchildrenToo` / `shutdownKillsDescendantsOfAdoptedOrphan`：桩进程树带常驻子进程，
  判据是临时目录 UUID 是否还出现在任何进程的命令行里（不依赖进程名，也就不会写成"看起来过了"）。
- `stopReportsNotFreedWhenPortIsStillListening`：端口被占时必须报 `freed=false`。
- `adoptsOwnSidecarWhenPidFileIsMissing` / `doesNotAdoptForeignSidecarWhenPidFileIsMissing`：
  pid 文件丢失后按 cwd 认亲，以及"别人的实例不许碰"。

### 真机实测（真实模型，非桩进程）

| 场景 | 结果 |
|---|---|
| 正常停止 | VRAM 6322 → 2134 MiB，0.16s，端口释放，无残留 python |
| 模拟崩溃（`taskkill /F` 硬杀 JVM） | VRAM 停在 6240 MiB、端口仍监听 —— 即事故现象复现 |
| 崩溃后重启 → 接管 → 停止 | 日志「接管上次遗留的本地语音 sidecar (PID 23424)」→ VRAM 6188 → 1975 MiB，pid 文件清理 |

### 补记：stop.ps1 的两个坑

- **编码**：PowerShell 5.1 对**没有 BOM** 的 `.ps1` 按系统 ANSI(cp936) 读，中文注释会被解坏，
  连语法都可能一起坏掉——实测第一版「跑完了、还打印了成功」但一个进程都没杀。文件必须以
  UTF-8 **带 BOM** 保存，并用 `powershell -NoProfile -File stop.ps1` 运行。
- **判据同样要验证**：脚本最后重新查一次端口，没释放就 `exit 1`，不能只打印"已结束"。
