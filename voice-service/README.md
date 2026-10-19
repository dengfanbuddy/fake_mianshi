# voice-service — 本地 Qwen3-TTS 语音合成 sidecar

给 `backend/`（Spring Boot）提供本地开源语音能力的 Python 服务。当前阶段只做 **TTS**（语音合成），
ASR（语音识别）仍由腾讯云提供，见 `docs/adr/0004-local-qwen-voice.md`。

模型：[Qwen3-TTS-12Hz-0.6B-CustomVoice](https://github.com/QwenLM/Qwen3-TTS)（Apache-2.0，9 个预置音色 + 自然语言风格控制），
推理加速：[faster-qwen3-tts](https://github.com/andimarafioti/faster-qwen3-tts)（MIT，CUDA graphs + 流式解码）。

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
pwsh -File scripts/download_model.ps1           # 从 ModelScope 下模型权重到 ./models
```

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

`/tts` 响应头带 `X-TTFA-Ms`（首包延迟）、`X-Rtf`（实时率，>1 表示比实时快）、`X-Audio-Seconds`。

后端对应实现：`backend/src/main/java/com/fakemianshi/service/LocalQwenVoiceService.java`。

## 实测数据（RTX 4070 Ti SUPER 16GB / Windows 11 / torch 2.14.1+cu126）

| 指标 | 数值 |
|---|---|
| 模型加载（含 CUDA graph 捕获） | 14.6–15.3s，启动时后台完成 |
| 显存占用 | 2.26GB 已分配 / 2.41GB 保留 |
| 首包延迟 TTFA | chunk=4 → **约 460ms**；chunk=8 → 约 700ms；chunk=12 → 约 900ms |
| 实时率 RTF | 1.06–1.38（>1 表示比实时快，播放不会断流） |
| 端到端（后端 `/api/voice/tts`，7.28s 音频） | 6.99s，RTF ≈ 1.04 |

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

未指定本地模型目录时走 HF 仓库；国内可设 `HF_ENDPOINT=https://hf-mirror.com`（`run.ps1` 已默认设置）。

## 已知限制

- **Windows TDR**：单次 GPU kernel 超过约 2 秒可能被驱动看门狗中断。本项目按句合成（一句几百毫秒），不会触发；若要一次性合成超长文本，需调大注册表 `TdrDelay`。
- **首次调用慢**：懒加载/未预热时第一句会等待模型加载（sidecar 已默认预加载，`/health` 可确认 `loaded`）。
- **并发**：模型非线程安全，服务内串行化（同一时刻只跑一个合成请求），多人同时面试建议部署多实例或排队。
- **ASR 未接入**：`/asr` 相关能力留到 P1/P2，见 ADR-0004。
