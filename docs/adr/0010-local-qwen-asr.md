# ADR-0010: 本地语音识别用 Qwen3-ASR（transformers 原生，与 TTS 同进程）

## 状态

已采纳（2026-10-20）

## 背景

ADR-0004 / ADR-0009 只解决了 TTS：面试官说话本地化了，但**候选人说话仍然要走腾讯云**。
这留下三个实际问题：

1. 页面一直挂着一句"未配置 AppId，实时语音识别不可用"。AppId 不是密钥，是腾讯云账号信息，
   用户往往只有 SecretId/SecretKey，于是**功能看起来配好了、实际一说话就失败**
   （`InterviewVoiceWebSocketHandler` 直接拒连），只能退回一句话识别。
2. 本地模式下"半本地半云"的体验不一致：合成免密钥，识别却要密钥，用户无法理解这个边界。
3. 面试回答内容（履历、项目细节）会发到第三方；既然 TTS 都能本地跑，识别没理由不行。

## 决策

用 **Qwen3-ASR** 做本地识别，跑在**已有的 voice-service 进程**里。

### 为什么是 transformers 原生 backend

| 方案 | 结论 |
|---|---|
| 官方 `qwen-asr` 包 | ❌ 会把 `transformers` 从 5.15.1 降到 4.57.6，而 TTS 依赖 5.15.1 的 `MimiConfig.rope_theta`，装上就崩 |
| vLLM（官方流式推理只支持它） | ❌ 没有 Windows 轮子，本项目是 Windows 单机分发 |
| **`transformers >= 5.13` 原生支持** | ✅ 当前 venv 的 5.15.1 已经带 `Qwen3ASRForConditionalGeneration` / `Qwen3ASRProcessor`，`librosa`/`soundfile` 也已就位——**零新增依赖、零新增进程** |

最后一条是决定性的：不需要第二个 venv、第二个 sidecar、第二套生命周期管理，
ADR-0009 那套自动拉起/关闭/接管逻辑原样复用。

### 模型选择

默认 **Qwen3-ASR-0.6B-hf**（0.8B 参数，bf16 约 1.5GB 显存），可通过
`QWEN_ASR_MODEL` 换成 1.7B（中文噪声/方言明显更准，但慢约 2.5 倍）。
0.6B 与 TTS 共驻后显存约 8GB / 16GB，仍有余量。

### 没有真流式，用「累积缓冲重识别」模拟

Qwen3-ASR 的流式推理只在 vLLM backend 提供。所以本地的「边说边出字」是这样实现的：

```
浏览器 PCM 分片 → LocalAsrSession 累积缓冲区
                    ├─ 每 ~2s 把「截至目前整段」重新识别 → partial（文本逐渐变长，不跳变）
                    └─ 松手 → 用完整缓冲识别一次 → final → 触发面试官回复
```

三个刹车，避免这套机制把显卡吃光、把关键路径拖慢：

1. **自我限速**：`下次延迟 >= 2 × 上次推理耗时`（上限 5s），保证 GPU 至少 2/3 空闲；
2. **音频上限**：超过 `partialMaxAudioSeconds`(30s) 停止刷新中间结果；
3. **迟到即弃**：`generation` 计数，松手后回来的中间结果直接丢掉，前端不会看到文本回退。

`partialIntervalMs=0` 可完全关掉中间结果：松手到出文本更快，显卡占用更低。

`partialMaxAudioSeconds` 默认 **6 秒**而不是无限：中间识别和松手后的最终识别抢同一把 GPU 锁，
实测 15s 的回答若让中间识别一直跑到最后，松手到出文本要 3.5s；限到 6s 之后是 1.94s。
代价是长回答的中间文本停在 6 秒处（最终文本仍完整）——这个取舍明显划算。

### 复用 GPU 锁

`app/gpu.py` 里一把 `GPU_LOCK`，TTS 与 ASR 的 `generate` 共用。两张模型同时生成不会算错，
但会在显存峰值上叠加，16GB 卡有 OOM 风险。**必须是同一把锁**，各自一把等于没加锁。

### 两张模型必须串行加载（踩过的坑）

第一版 lifespan 里给 TTS 和 ASR 各起了一个预加载线程，结果：

- ASR 线程 `import torch / transformers` 与 TTS 线程 `import faster_qwen3_tts` 撞车，抛出 `ImportError`；
- TTS 的降级分支恰好 `except ImportError`，于是**静默降级成官方 backend**；
- 后果是 TTS 的 RTF 从 ~2.0 掉到 ~0.29（慢 7 倍），日志里只有一句"未安装 faster-qwen3-tts"，`kind` 字段
  也从 `faster` 变成 `official`——不看 `/health` 根本发现不了。

修法两条：① 只起**一个**加载线程，按 TTS → ASR 顺序串行加载；② 降级分支必须把异常本体打进日志。
这个坑值得记下来，因为它属于"功能都正常、只是慢了 7 倍"的隐蔽故障。

## 实测数据（RTX 4070 Ti SUPER 16GB）

| 指标 | 结果 |
|---|---|
| 模型加载（两张串行，含 warmup） | TTS 与 ASR 合计约 35s，后台完成 |
| 两模型共驻显存 | **8118 MiB / 16376 MiB** |
| TTS（faster backend，两模型都加载后） | TTFA 842ms，RTF 1.14 |
| ASR 官方中文样本 3.6s | 614ms → **RTF 5.9**，文本逐字一致 |
| ASR 裸 PCM16 同一段（WS 路径） | 562ms → **RTF 6.4**，同为逐字一致 |
| ASR 13.5s 长句 | 1.94s → **RTF 6.9**，逐字一致 |
| TTS→ASR 闭环 4 条面试话术（38.9s） | 6.16s → **6.3x 实时**；2 条逐字一致，另 2 条只有标点/口语省略差异 |
| 整链路（后端 WS，15.2s 回答） | 松手 → 识别文本 **1.94s**，中间结果 2 次 |
| 热词偏置效果 | 无热词时 "JVM"→"JYEM"、"gRPC"→"GRPC"、"etcd"→"ATCD"；加对应写法后逐字一致 |
| 静音 / <0.2s | 短路不送模型，返回空文本（避免对噪声"幻听"） |

### 热词在页面上可编辑

热词效果强依赖用户的技术栈（Java/Go/前端各不同），写死在 `application.yml` 里等于是让用户改配置文件。
所以 `voice_config.asr_prompt` 落库、设置页可编辑：

- 优先级：页面值 > `app.voice.local.asr.prompt`（内置默认表）；
- **留空 = 用内置默认，不是"不要热词"**——内置表有实测收益，真清空属于反直觉且有害的操作；
- 长度上限 500 字：不是为省 token，而是热词表越长偏置越被稀释、实测会变差；
- 页面同时下发 `asrPromptDefault`（内置表原文），既做 placeholder 也支持「填入内置默认」后二次编辑。

一个必须写进 UI 的实测细节：**热词的大小写会被照抄**。同一段音频，
`etcd` 纠正不了（仍输出 ATCD），写成 `ETCD` 才输出 ETCD。所以提示文案要求用户
"按希望输出的写法填写"。

## 后果

- 本地模式下 TTS+ASR 都不需要腾讯云密钥；腾讯云配置退化为「关闭本地识别/本地异常时」的回退项。
  回退策略沿用 `fallback-to-tencent`，且两边都失败时**优先报本地原因**（用户刚配的是本地）。
- `InterviewVoiceWebSocketHandler` 只依赖新的 `AsrSession` 接口，腾讯云与本地是两个实现，
  业务侧（LLM 流式回复、录音落盘）完全不感知差异。
- 12s 的回答大约 1.6s 出最终文本；1.7B 会更准但更慢，属于可配置的取舍。
- 保留腾讯云路径：它是唯一能真流式的选择（延迟更低），本地方案不追求在这点上追平。

## 相关

- ADR-0004：本地 Qwen3-TTS 接入
- ADR-0009：sidecar 生命周期由后端托管
- `voice-service/app/asr_engine.py`、`backend/.../voice/LocalAsrSession.java`
- `voice-service/scripts/bench_asr.py`（TTS→ASR 闭环基准）
