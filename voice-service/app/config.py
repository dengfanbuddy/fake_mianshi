"""voice-service 配置：全部通过环境变量覆盖，便于打包分发。"""

from __future__ import annotations

import os
from pathlib import Path


def _detect_local_model() -> str | None:
    """自动发现 voice-service/models 下已下载的模型目录（含 config.json 的目录）。

    多个时优先 CustomVoice（本服务只用 CustomVoice 的预置音色），否则取第一个。
    """
    models_dir = Path(__file__).resolve().parent.parent / "models"
    if not models_dir.is_dir():
        return None
    candidates = [p for p in sorted(models_dir.iterdir())
                  if p.is_dir() and (p / "config.json").is_file()]
    if not candidates:
        return None
    for path in candidates:
        if "CustomVoice" in path.name:
            return str(path)
    return str(candidates[0])

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
