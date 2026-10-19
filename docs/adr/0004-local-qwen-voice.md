# ADR-0004: 语音能力本地化 — 本地 Qwen3-TTS sidecar（TTS 先落地，ASR 暂时保留腾讯云）

**Date:** 2026-10-19
**Status:** Accepted（TTS 部分已实现；ASR 部分见「后续」）

## 背景

ADR-0003 选定腾讯云 ASR + TTS。实际使用后暴露三个问题：

1. **音色机械且区分度低**：面试官 `styleConfig` 的 `tone/speed` 只能映射到有限的腾讯云音色编号（1018/1010/1001/1004/1009），"技术深挖"和"压力面"听感差别有限，人设的语音表现力弱。
2. **调用费用与网络依赖**：每句回复都要走云端 TTS，离线/内网环境不可用，且长对话有持续成本。
3. **可控性不足**：腾讯云 TTS 无法按自然语言描述调节语气（"语速偏快、带压迫感"），也无法在本地克隆/设计音色。

同期（2026-01）Qwen 团队开源了 Qwen3-TTS 与 Qwen3-ASR（均 Apache-2.0）：

- Qwen3-TTS 0.6B/1.7B：9 个预置音色 + VoiceDesign（自然语言设计音色）+ 3 秒克隆 + instruct 控制情绪语速；
- Qwen3-ASR 0.6B/1.7B：30 语言 + 22 种中文方言，中文方言/口音鲁棒性显著优于 Whisper-large-v3。

## 决策

### 1. 引入 Python sidecar：`voice-service/`

浏览器 → Spring Boot（编排）→ `voice-service`（Python，FastAPI，本地 GPU 推理）→ 返回音频。

- 新增 `voice-service/`：`faster-qwen3-tts`（MIT，CUDA graphs + 流式解码）跑 `Qwen3-TTS-12Hz-0.6B-CustomVoice`；
- 对外暴露 `POST /tts`（16k wav）与 `POST /tts/stream`（16k PCM 分块）、`GET /health`；
- 采样率统一重采样到 **16k**，与现有 `frontend/src/utils/audioQueue.js` 的 `PcmQueuePlayer(16000)` 和 `WavAudio` 假设保持一致。

不选 CosyVoice / FunASR / Whisper 的原因：Qwen3-TTS 的 instruct 控制与预置音色最贴合「面试官人设」这一产品需求；
CosyVoice3 内容一致性略优但可控性弱、生态更散；ASR 侧 Qwen3-ASR 中文方言指标领先（KeSpeech 5.10 vs Whisper 28.79）。

### 2. 后端加「供应商路由」，不破坏既有接口

- `VoiceServiceRouter`（`@Primary`）实现 `VoiceService`，按当前生效配置把调用分发给
  `VoiceServiceImpl`（腾讯云）或 `LocalQwenVoiceService`（本地）；
- 上层 `MockInterviewServiceImpl`（逐句流式 TTS）、`VoiceController` 等**零改动**——它们只依赖 `VoiceService` 接口；
- 供应商切换走既有的「保存配置并激活」流程：`voice_config.provider = local_qwen`，`tts_url` 存 sidecar 根地址。
  `VoiceConfig.isConfigured()` 对本地供应商只要求地址非空，不要求密钥；
- 人设风格 → 音色的映射从「腾讯云音色编号」改为「音色 + 自然语言 instruct」，并支持人设 JSON 里
  用 `qwenVoice` / `qwenInstruct` / `qwenLanguage` 显式覆盖。

### 3. ASR 暂不切换

本地 Qwen3-ASR 的**流式推理只有 vLLM 后端支持**，而 vLLM 在原生 Windows 上不可用（需 WSL2/Docker），
当前开发机两者都没有。因此本阶段：

- **TTS 走本地 Qwen**（已实现）；
- **ASR 继续走腾讯云**（含 WebSocket 实时识别）；
- `VoiceServiceRouter.recognizeSpeech` 固定转发腾讯云，并在本地供应商模式下给出明确错误提示。

## 实测数据（RTX 4070 Ti SUPER 16GB / Windows 11，Qwen3-TTS-12Hz-0.6B-CustomVoice）

| 指标 | 数值 |
|---|---|
| 模型加载（含 CUDA graph 捕获） | 14.6–15.3s（sidecar 启动即后台预热） |
| 显存占用 | 2.26GB 已分配 / 2.41GB 保留 |
| 首包延迟 TTFA | chunk=4 约 460ms / chunk=8 约 700ms / chunk=12 约 900ms |
| 实时率 RTF | 1.06–1.38（>1 即比实时快，逐句播放不断流） |
| 端到端（HTTP `/api/voice/tts`，7.28s 音频） | 6.99s，RTF ≈ 1.04 |

结论：0.6B 模型在这张卡上约留 2.5GB 显存，配合 1.7B ASR（约 4–5GB）总占用 8GB 以内，后续接 ASR 有充足余量。

## 后果

**收益**

- 面试官音色自然度与区分度显著提升；5 个预置人设可用自然语言描述语气，不再受限于云厂商音色编号。
- 本地推理无调用费用，可离线运行（LLM 仍走 DeepSeek API）。
- 为后续「音色设计 / 声音克隆（用真人面试官声音）」留出空间。

**代价与限制**

- 需要一块 NVIDIA GPU（0.6B 约 3–4GB 显存）与常驻 Python 进程；部署多一个组件，需脚本守护。
- 首次加载 30–60s（sidecar 启动即预热，`/health` 可查状态）。
- 模型非线程安全，服务内串行化：多人同时面试会排队，需要时横向扩实例。
- 增加一层网络跳转（本机 HTTP），单句 TTS 多约 5–15ms 开销，可忽略。
- 已实现失败回退腾讯云（`app.voice.local.fallback-to-tencent`），避免本地服务挂掉导致语音整体不可用。

## 后续（未实现，按需推进）

1. **P1：本地 ASR 整段识别** — `qwen-asr` transformers 后端（Windows 原生可跑），候选人录音整段识别 + 腾讯云回退，做方言/口音 A/B。
2. **P2：真流式 ASR** — 安装 WSL2 + vLLM，`qwen-asr` 流式推理接回 `partial` 中间结果（当前 WS 协议的 `partial` 事件即可复用）。
3. **P3：音色设计/克隆** — 用 VoiceDesign 为 5 个预置人设各生成一把固定嗓子，再用 Base 模型克隆固化，保证跨会话一致。

## 参考

- [Qwen3-TTS](https://github.com/QwenLM/Qwen3-TTS) / [Qwen3-ASR](https://github.com/QwenLM/Qwen3-ASR)（Apache-2.0）
- [faster-qwen3-tts](https://github.com/andimarafioti/faster-qwen3-tts)（MIT，实时流式推理）
- 实现细节见 `voice-service/README.md`
