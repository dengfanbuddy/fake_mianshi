# voice-service — 本地 Qwen3 语音 sidecar（TTS + ASR）

给 `backend/`（Spring Boot）提供本地开源语音能力的 Python 服务：**TTS（面试官朗读）+ ASR（识别候选人说话）**
跑在同一个进程里，本地模式下完全不需要腾讯云密钥。设计取舍见
`docs/adr/0004-local-qwen-voice.md` 与 `docs/adr/0010-local-qwen-asr.md`。

| 能力 | 模型 | 说明 |
|---|---|---|
| TTS | [Qwen3-TTS-12Hz-0.6B-CustomVoice](https://github.com/QwenLM/Qwen3-TTS)（Apache-2.0） | 9 个预置音色 + 自然语言风格控制；推理加速用 [faster-qwen3-tts](https://github.com/andimarafioti/faster-qwen3-tts) |
| ASR | [Qwen3-ASR-0.6B](https://github.com/QwenLM/Qwen3-ASR)（Apache-2.0） | 52 语言 + 22 种中文方言，走 **transformers 原生 backend**（≥5.13） |

> ASR 特意不用官方 `qwen-asr` 包：它会把 `transformers` 降到 4.57，直接破坏 Qwen3-TTS 的
> `MimiConfig.rope_theta`。当前 venv 的 `transformers==5.15.1` 已原生支持 Qwen3-ASR，
> 因此**零新增依赖、零新增进程**。真流式推理只有 vLLM 提供，而 vLLM 没有 Windows 轮子，
> 所以本服务的「边说边出字」由后端按累积缓冲重识别实现，不是模型级流式。

## 为什么需要它

项目原来把面试官 `styleConfig` 的 `tone/speed` 映射成腾讯云音色编号（1018/1010/1001…），
音色机械、且不同人设区分度低。Qwen3-TTS 允许用自然语言直接描述语气（"语速偏快、带压迫感"），
配合 9 个预置音色，人设区分度和自然度都有明显提升，且本地推理无调用费用。

## 环境要求

- Windows / Linux，NVIDIA GPU（0.6B 模型约 3–4GB 显存；RTX 4060 级别即可实时）
- Python 3.12（`.python-version` 已固定）
- [uv](https://docs.astral.sh/uv/)

## 安装

```powershell
cd voice-service
uv sync                                        # 装依赖（torch 走 CUDA 轮子源，首次约 2-3GB）
pwsh -File scripts/download_model.ps1           # TTS 权重 → ./models（约 2.4GB）
pwsh -File scripts/download_model.ps1 -Model Qwen/Qwen3-ASR-0.6B-hf   # ASR 权重 → ./models（约 1.5GB）
```

两张模型都放在 `./models` 下即可，服务会按目录名自动发现（TTS 优先匹配 `CustomVoice`，
ASR 匹配名字里含 `ASR` 的目录），也可以用 `QWEN_TTS_MODEL` / `QWEN_ASR_MODEL` 显式指定。

`scripts/download_model.ps1` 默认下载 `Qwen/Qwen3-TTS-12Hz-0.6B-CustomVoice`。
若想用 1.7B（音质更好、更吃显存）：

```powershell
pwsh -File scripts/download_model.ps1 -Model Qwen/Qwen3-TTS-12Hz-1.7B-CustomVoice
$env:QWEN_TTS_MODEL = "E:\project2\AI\fake_mianshi\voice-service\models\Qwen3-TTS-12Hz-1.7B-CustomVoice"
```

## 启动

```powershell
pwsh -File run.ps1            # 默认 http://127.0.0.1:9100
pwsh -File run.ps1 -Port 9200 -Reload
```

启动后模型在后台线程加载（首次 30–60s），接口立即可用；`GET /health` 会从 `loading` 变 `ok`。

## 自检（推荐先跑这个）

```powershell
uv run python selfcheck.py
# 输出：加载耗时 / 首次与稳态 TTFA、RTF / 显存占用，并写出 selfcheck_out.wav 供试听
```

## 接口

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/health` | 模型状态、可用音色、采样率、加载耗时与错误 |
| GET | `/speakers` | 可用音色列表 |
| POST | `/warmup` | 手动加载/预热（加载失败后重试也用它） |
| POST | `/tts` | 入参 `{text, speaker, instruct, language}`，返回 **16k/16bit/单声道 wav** |
| POST | `/tts/stream` | 同上入参，返回 **16k PCM 裸流**分块响应 |
| POST | `/asr` | 请求体是音频：`audio/wav`（或 soundfile 认得的格式）或 `application/octet-stream` 裸 PCM16LE（配 `X-Sample-Rate` 头） |
| POST | `/asr/warmup` | 手动加载/预热 ASR 模型 |

`/tts` 响应头带 `X-TTFA-Ms`（首包延迟）、`X-Rtf`（实时率，>1 表示比实时快）、`X-Audio-Seconds`。

`/asr` 返回 JSON：`{text, language, audio_seconds, infer_ms, rtf, skipped}`，并带 `X-Rtf` / `X-Infer-Ms` 头。
支持查询参数 `?language=Chinese`（强制语种，缺省自动检测）与 `?prompt=术语：Redis、幂等`（热词偏置）。
不足 0.2s 或纯静音会直接返回空文本（`skipped` 字段说明原因），不送模型——否则模型会对着噪声"幻听"。

后端对应实现：`backend/src/main/java/com/fakemianshi/service/LocalQwenVoiceService.java`。

## 实测数据（RTX 4070 Ti SUPER 16GB / Windows 11 / torch 2.14.1+cu126）

| 指标 | 数值 |
|---|---|
| 模型加载（含 CUDA graph 捕获） | 14.6–15.3s，启动时后台完成 |
| 显存占用 | 2.26GB 已分配 / 2.41GB 保留 |
| 首包延迟 TTFA | chunk=4 → **约 460ms**；chunk=8 → 约 700ms；chunk=12 → 约 900ms |
| 实时率 RTF | 1.06–1.38（>1 表示比实时快，播放不会断流） |
| 端到端（后端 `/api/voice/tts`，7.28s 音频） | 6.99s，RTF ≈ 1.04 |

### ASR 实测（同一台机器，0.6B / bf16）

| 指标 | 数值 |
|---|---|
| 模型加载（含 warmup） | 17.8s（与 TTS 串行加载，总显存约 8GB / 16GB） |
| 官方中文样本 3.6s | 614ms → **RTF 5.9**，文本逐字一致 |
| 裸 PCM16 同一段（WS 路径） | 562ms → **RTF 6.4**，同为逐字一致 |
| TTS→ASR 闭环 4 条面试话术（38.9s） | 6.16s → **6.3x 实时**，2 条逐字一致、2 条仅标点/口语省略差异 |
| ASR 13.5s 长句 | 1.94s → **RTF 6.9**，逐字一致 |
| 整链路（后端 WS，15.2s 回答） | 松手 → 识别文本 **1.94s**，中间结果 2 次逐步变长 |
| 两模型共驻显存 | **8118 MiB / 16376 MiB**（TTS faster backend TTFA 842ms / RTF 1.14） |

跑法：`uv run python scripts/bench_asr.py`（TTS→ASR 闭环基准）、`scripts/e2e_ws_asr.py`（后端 WS 全链路）。

> 官方文档的 97ms 首包是数据中心级 GPU 的数字；社区基准里 RTX 4060(Windows) 报 0.6B TTFA 413ms / RTF 2.26，
> 本机实测 RTF 约 1.1–1.4，比该基准保守，但足以支撑逐句播报。
> 首包想更快就把 `QWEN_TTS_CHUNK_SIZE` 调小（下限 1，越大吞吐越高）。

## 音色与风格

预置音色（CustomVoice）：`Vivian`、`Serena`、`Uncle_Fu`、`Dylan`（北京话）、`Eric`（成都话）、
`Ryan`、`Aiden`、`Ono_Anna`、`Sohee`。

人设（`styleConfig`）到音色/指令的映射在 Java 侧完成，支持在人设 JSON 里显式覆盖：

```json
{
  "tone": "stern",
  "speed": "fast",
  "aggressiveness": 0.9,
  "qwenVoice": "Dylan",
  "qwenInstruct": "强势、直接的面试官，语速偏快，带一点压迫感",
  "qwenLanguage": "Chinese"
}
```

## 环境变量

| 变量 | 默认值 | 说明 |
|---|---|---|
| `QWEN_TTS_MODEL` | `Qwen/Qwen3-TTS-12Hz-0.6B-CustomVoice` | 模型仓库名或本地目录 |
| `QWEN_TTS_DEVICE` | `cuda` | `cuda` / `cpu` |
| `QWEN_TTS_BACKEND` | `torch` | faster-qwen3-tts 后端：`torch` / `ggml` |
| `QWEN_TTS_CHUNK_SIZE` | `8` | 流式分块步数（8 步 ≈ 667ms 音频，越小首包越快） |
| `VOICE_SAMPLE_RATE` | `16000` | 输出采样率（对齐前端 `PcmQueuePlayer`） |
| `VOICE_PORT` | `9100` | 监听端口 |
| `VOICE_PRELOAD` | `1` | 启动即加载并预热 |
| `QWEN_TTS_CACHE` | `./.cache` | HF 下载缓存目录 |
| `VOICE_ASR_ENABLED` | `1` | 是否启用本地识别（关掉则 `/asr` 返回 503） |
| `QWEN_ASR_MODEL` | 自动发现 / `Qwen/Qwen3-ASR-0.6B-hf` | ASR 权重路径或仓库名；换 1.7B 更准但慢约 2.5 倍 |
| `QWEN_ASR_LANGUAGE` | 空 | 强制语种，空 = 自动检测 |
| `QWEN_ASR_PROMPT` | 空 | 领域热词（system prompt 偏置），如 `术语：Redis、幂等、熔断降级` |
| `QWEN_ASR_MAX_NEW_TOKENS` | `256` | 解码上限，够一分钟以上的回答 |
| `QWEN_ASR_DTYPE` | `bfloat16` | 推理精度 |
| `VOICE_ASR_PRELOAD` | `1` | 启动即预加载 ASR |
| `VOICE_ASR_MAX_AUDIO_SECONDS` | `120` | 单次识别音频上限，超出截断 |

未指定本地模型目录时走 HF 仓库；国内可设 `HF_ENDPOINT=https://hf-mirror.com`（`run.ps1` 已默认设置）。

## 已知限制

- **Windows TDR**：单次 GPU kernel 超过约 2 秒可能被驱动看门狗中断。本项目按句合成（一句几百毫秒），不会触发；若要一次性合成超长文本，需调大注册表 `TdrDelay`。
- **首次调用慢**：懒加载/未预热时第一句会等待模型加载（sidecar 已默认预加载，`/health` 可确认 `loaded`）。
- **并发**：TTS 与 ASR 共用一把 `GPU_LOCK`（`app/gpu.py`）串行化——两张模型同时生成不会算错，
  但显存峰值会叠加，16GB 卡有 OOM 风险。多人同时面试建议部署多实例。
- **两张模型必须串行加载**：并发预加载会让 ASR 侧的 `import torch/transformers` 撞上 TTS 侧的
  `import faster_qwen3_tts` 并抛 `ImportError`，而 TTS 会把 `ImportError` 当成"没装"静默降级成
  慢 7 倍的官方 backend（RTF 2.0 → 0.29）。`/health` 的 `kind` 字段可确认是 `faster` 还是 `official`。
- **ASR 无真流式**：流式推理只在 vLLM backend 提供（无 Windows 轮子）。本服务的"边说边出字"是
  后端每 ~2s 把累积音频重新识别一次，靠"自我限速（留 2/3 空闲）+ 音频上限 + 迟到即弃"控制开销；
  把 `app.voice.local.asr.partial-interval-ms` 设为 `0` 可完全关掉，松手后出文本更快。
- **静音/短音频短路**：<0.2s 或能量低于阈值的音频直接返回空文本，这是刻意的——避免模型对噪声"幻听"。
