"""voice-service 配置：全部通过环境变量覆盖，便于打包分发。"""

from __future__ import annotations

import os
from pathlib import Path


def _model_candidates(match: str) -> list[Path]:
    """voice-service/models 下含 config.json 的模型目录，名字命中 ``match`` 的排前面。"""
    models_dir = Path(__file__).resolve().parent.parent / "models"
    if not models_dir.is_dir():
        return []
    found = [p for p in sorted(models_dir.iterdir())
             if p.is_dir() and (p / "config.json").is_file()]
    return [p for p in found if match in p.name] + [p for p in found if match not in p.name]


def _detect_local_model() -> str | None:
    """自动发现 TTS 模型目录（优先 CustomVoice：本服务只用它的预置音色）。

    注意必须排除 ASR 目录，否则装了两张模型后可能把 ASR 权重当 TTS 加载。
    """
    candidates = [p for p in _model_candidates("CustomVoice") if "ASR" not in p.name]
    return str(candidates[0]) if candidates else None

def _detect_local_asr_model() -> str | None:
    """自动发现 ASR 模型目录（名字含 ASR）。"""
    candidates = [p for p in _model_candidates("ASR") if "ASR" in p.name]
    return str(candidates[0]) if candidates else None

BASE_DIR = Path(__file__).resolve().parent.parent

# 模型权重：优先用环境变量；其次自动发现 ./models 下的本地目录（scripts/download_model.ps1 的产物）；
# 最后回落到 HF / ModelScope 仓库名（运行时会联网下载）
MODEL_PATH = os.getenv("QWEN_TTS_MODEL") or _detect_local_model() or "Qwen/Qwen3-TTS-12Hz-0.6B-CustomVoice"

# 模型缓存目录（HF 下载时使用）
CACHE_DIR = os.getenv("QWEN_TTS_CACHE", str(BASE_DIR / ".cache"))

# 推理设备：cuda / cpu
DEVICE = os.getenv("QWEN_TTS_DEVICE", "cuda")

# 推理后端：torch（faster-qwen3-tts CUDA graphs） / ggml / auto
BACKEND = os.getenv("QWEN_TTS_BACKEND", "torch")

# 流式输出分块：每 N 步解码产出一块音频（4 步 ≈ 333ms 音频）。
# 实测 RTX 4070 Ti SUPER：chunk=4 → 首包约 460ms / RTF 1.16；chunk=8 → 首包约 700ms / RTF 1.25
CHUNK_SIZE = int(os.getenv("QWEN_TTS_CHUNK_SIZE", "4"))

# 服务监听
HOST = os.getenv("VOICE_HOST", "127.0.0.1")
PORT = int(os.getenv("VOICE_PORT", "9100"))

# 输出采样率：项目前端 PcmQueuePlayer 按 16k 播放，故默认重采样到 16000
OUTPUT_SAMPLE_RATE = int(os.getenv("VOICE_SAMPLE_RATE", "16000"))

# 单次合成文本上限（防止误传长文导致长时间占用 GPU）
MAX_TEXT_CHARS = int(os.getenv("VOICE_MAX_TEXT_CHARS", "500"))

# 启动即预加载并 warmup（关掉则等第一个请求时懒加载）
PRELOAD = os.getenv("VOICE_PRELOAD", "1") not in ("0", "false", "False")

# 默认语言
DEFAULT_LANGUAGE = os.getenv("QWEN_TTS_LANGUAGE", "Chinese")


# ---------------- ASR（语音识别，Qwen3-ASR） ----------------
# 关掉后 /asr 返回 503，后端会自动回退腾讯云（若已配置密钥）
ASR_ENABLED = os.getenv("VOICE_ASR_ENABLED", "1") not in ("0", "false", "False")

# 权重：优先环境变量；其次 models/ 下名字含 ASR 的目录；最后回落 HF 仓库名（会联网下载）
ASR_MODEL = os.getenv("QWEN_ASR_MODEL") or _detect_local_asr_model() or "Qwen/Qwen3-ASR-0.6B-hf"

# 推理设备 / 精度（4070 Ti SUPER 走 bf16）
ASR_DEVICE = os.getenv("QWEN_ASR_DEVICE", DEVICE)
ASR_DTYPE = os.getenv("QWEN_ASR_DTYPE", "bfloat16")

# 启动即预加载（关掉则等第一个 /asr 请求时懒加载，首包会慢十几秒）
ASR_PRELOAD = os.getenv("VOICE_ASR_PRELOAD", "1") not in ("0", "false", "False")

# 解码上限：中文正常语速 1 秒 ≈ 4~5 字，256 token 足够一分钟以上的回答
ASR_MAX_NEW_TOKENS = int(os.getenv("QWEN_ASR_MAX_NEW_TOKENS", "256"))

# 强制语种（Chinese / zh）；空表示让模型自动检测（会多输出一个 language 字段）
ASR_LANGUAGE = os.getenv("QWEN_ASR_LANGUAGE", "")

# 领域热词（走 system prompt 偏置）。面试场景专有名词多，填了能明显减少同音错字，
# 例如：术语：Kubernetes、Redis、分库分表、幂等、熔断降级。
ASR_PROMPT = os.getenv("QWEN_ASR_PROMPT", "")

# 单次识别音频上限（秒）：防误把整场录音塞进来，正常一轮回答远小于此值
ASR_MAX_AUDIO_SECONDS = float(os.getenv("VOICE_ASR_MAX_AUDIO_SECONDS", "120"))
