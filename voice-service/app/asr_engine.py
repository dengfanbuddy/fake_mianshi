"""Qwen3-ASR 推理封装（transformers 原生 backend）。

为什么是「原生 transformers」而不是官方 ``qwen-asr`` 包或 vLLM：

- ``transformers`` 从 5.13 起原生支持 Qwen3-ASR（``Qwen3ASRForConditionalGeneration``
  + ``Qwen3ASRProcessor``），而本项目的 TTS 已经锁在 ``transformers==5.15.1``。
  于是**不需要新 venv、不需要第二个进程**，同一个 sidecar 里就能跑两张模型。
  （``qwen-asr`` 包会把 transformers 降到 4.57，直接破坏 Qwen3-TTS 的
  ``MimiConfig.rope_theta``，实测冲突。）
- 官方流式推理只支持 vLLM backend，而 vLLM 没有 Windows 轮子。所以本地只做
  **整段离线识别**；「边说边出字」由后端按累积缓冲重识别来实现（见
  ``InterviewVoiceWebSocketHandler`` 的 LocalAsrSession），不是模型级真流式。

设计要点：

- 与 TTS 共用 ``GPU_LOCK`` 串行化（见 ``app/gpu.py``）。
- 懒加载 + 后台预热：``/health`` 立刻可答，模型在后台加载。
- 短音频（<0.2s）和纯静音直接返回空文本，避免模型对着噪声「幻听」。
"""

from __future__ import annotations

import io
import logging
import threading
import time
from typing import Any

import numpy as np

from . import config
from .gpu import GPU_LOCK

log = logging.getLogger("voice-service.asr")

# 纯静音的 RMS 阈值（float32 归一化幅度）。16k 采集的安静房间通常 < 0.005。
SILENCE_RMS = 0.004
# 低于这个时长不必送模型（录音误触发的抖动）
MIN_AUDIO_SECONDS = 0.2


def decode_audio(data: bytes, sample_rate: int = 16000) -> tuple[np.ndarray, int]:
    """把请求体字节解码为 ``(float32 单声道, 采样率)``。

    两种输入形态：

    - 带文件头（RIFF/AIFF/FLAC/Ogg）→ 交给 soundfile，采样率以文件为准；
    - 裸 PCM16LE 单声道（后端 WebSocket 路径直接拿到的就是这种，没有头）
      → 按 ``sample_rate`` 解释。
    """
    if not data:
        raise ValueError("音频数据为空")

    head = data[:4]
    if head in (b"RIFF", b"FORM", b"OggS", b"fLaC") or data[:3] == b"ID3":
        import soundfile as sf

        arr, sr = sf.read(io.BytesIO(data), dtype="float32", always_2d=False)
        if arr.ndim > 1:
            arr = arr.mean(axis=1)  # 多声道降混，面试录音不该是立体声
        return np.ascontiguousarray(arr, dtype=np.float32), int(sr)

    # 裸 PCM16：截到偶数长度，避免末尾半样本被当成噪声
    usable = len(data) - (len(data) % 2)
    pcm = np.frombuffer(data[:usable], dtype="<i2")
    return (pcm.astype(np.float32) / 32768.0), int(sample_rate)


def _trim_digital_silence(audio: np.ndarray, threshold: float = SILENCE_RMS) -> np.ndarray:
    """掐掉首尾静音，减少送进编码器的无效帧（对首字准确率有实际帮助）。"""
    if audio.size == 0:
        return audio
    # 20ms 一帧算能量
    frame = max(1, int(0.02 * 16000))
    n = audio.size // frame
    if n == 0:
        return audio
    energy = np.sqrt(np.mean(audio[: n * frame].reshape(n, frame) ** 2, axis=1))
    voiced = np.nonzero(energy > threshold)[0]
    if voiced.size == 0:
        return np.zeros(0, dtype=np.float32)
    start = max(0, voiced[0] - 5) * frame          # 保留 100ms 前沿
    end = min(n, voiced[-1] + 6) * frame           # 保留 120ms 后沿
    return audio[start:min(end, audio.size)]


class AsrEngine:
    """懒加载的 Qwen3-ASR 引擎。"""

    def __init__(self) -> None:
        self.model: Any = None
        self.processor: Any = None
        self.model_path: str = config.ASR_MODEL
        self.device: str = config.ASR_DEVICE
        self.dtype_name: str = config.ASR_DTYPE
        self.load_error: str | None = None
        self.load_seconds: float | None = None
        self._torch: Any = None
        self._dtype: Any = None
        self._lock = threading.Lock()

    # ---------------- 加载 ----------------

    @property
    def enabled(self) -> bool:
        return config.ASR_ENABLED

    @property
    def ready(self) -> bool:
        return self.model is not None

    def ensure_loaded(self) -> None:
        if self.ready:
            return
        if not self.enabled:
            raise RuntimeError("ASR 未启用（VOICE_ASR_ENABLED=0）")
        with self._lock:
            if self.ready:
                return
            started = time.time()
            try:
                self._load()
                self.load_error = None
            except Exception as exc:  # noqa: BLE001 - 失败原因要给 /health 和后端看
                self.load_error = f"{type(exc).__name__}: {exc}"
                log.exception("ASR 模型加载失败: %s", self.load_error)
                raise
            finally:
                self.load_seconds = round(time.time() - started, 1)

    def _load(self) -> None:
        import torch
        from transformers import AutoProcessor, Qwen3ASRForConditionalGeneration

        self._torch = torch
        self._dtype = getattr(torch, self.dtype_name, torch.bfloat16)

        log.info("加载 Qwen3-ASR: %s (device=%s dtype=%s)",
                 self.model_path, self.device, self.dtype_name)
        self.processor = AutoProcessor.from_pretrained(self.model_path)
        model = Qwen3ASRForConditionalGeneration.from_pretrained(
            self.model_path,
            dtype=self._dtype,
            device_map=self.device,
        )
        model.eval()
        self.model = model
        self._warmup()
        log.info("ASR 就绪: 加载耗时=%.1fs 显存=%.0fMB", self.load_seconds or 0, self._vram_mb())

    def _vram_mb(self) -> float:
        try:
            return self._torch.cuda.memory_allocated() / 1024 / 1024
        except Exception:  # noqa: BLE001
            return 0.0

    def _warmup(self) -> None:
        """用 0.6s 静音跑一遍，把 CUDA kernel / 显存池分配提前做掉。"""
        try:
            silence = np.zeros(int(16000 * 0.6), dtype=np.float32)
            started = time.time()
            with GPU_LOCK:
                self._run(silence, 16000, config.ASR_LANGUAGE or None, config.ASR_PROMPT or None)
            log.info("ASR warmup 完成，耗时 %.2fs", time.time() - started)
        except Exception as exc:  # noqa: BLE001 - warmup 失败不影响后续可用性
            log.warning("ASR warmup 失败（可忽略）: %s", exc)

    # ---------------- 识别 ----------------

    def _run(self, audio: np.ndarray, sample_rate: int, language: str | None,
             prompt: str | None) -> dict:
        """调用方已持有 GPU_LOCK。"""
        inputs = self.processor.apply_transcription_request(
            audio=audio,
            language=language,
            prompt=prompt,
        )
        inputs = inputs.to(self.device, self._dtype)
        with self._torch.inference_mode():
            output_ids = self.model.generate(**inputs, max_new_tokens=config.ASR_MAX_NEW_TOKENS)
        generated = output_ids[:, inputs["input_ids"].shape[1]:]
        return self.processor.decode(generated, return_format="parsed")[0]

    def transcribe(self, data: bytes | np.ndarray, sample_rate: int = 16000,
                   language: str | None = None, prompt: str | None = None) -> dict:
        """识别一段音频。

        :param data: WAV 等带头的字节流，或裸 PCM16 字节流，或已解码的 float32 数组
        :param sample_rate: 裸 PCM / 数组的采样率
        :param language: 强制语种（``Chinese`` / ``zh``），None 表示自动检测
        :param prompt: 领域热词/上下文（system prompt 偏置），None 用环境变量里的默认值
        :return: ``{text, language, audio_seconds, infer_ms, rtf, skipped}``
        """
        self.ensure_loaded()
        if isinstance(data, np.ndarray):
            audio, sr = np.ascontiguousarray(data, dtype=np.float32), sample_rate
        else:
            audio, sr = decode_audio(data, sample_rate)

        if sr != 16000 and audio.size:
            from .tts_engine import _resample  # 复用已实现的 soxr HQ 重采样

            audio = _resample(audio, sr, 16000)

        raw_seconds = audio.size / 16000.0
        audio = _trim_digital_silence(audio)
        if audio.size / 16000.0 < MIN_AUDIO_SECONDS:
            return {
                "text": "",
                "language": None,
                "audio_seconds": round(raw_seconds, 2),
                "infer_ms": 0.0,
                "rtf": 0.0,
                "skipped": "silence-or-too-short",
            }

        # 超长音频截断（正常一轮回答不会超过两分钟，截断只为防误传整场录音）
        limit = int(config.ASR_MAX_AUDIO_SECONDS * 16000)
        if audio.size > limit:
            log.warning("音频 %.1fs 超过上限 %.1fs，已截断", audio.size / 16000, config.ASR_MAX_AUDIO_SECONDS)
            audio = audio[:limit]

        seconds = audio.size / 16000.0
        started = time.time()
        effective_language = (language or "").strip() or (config.ASR_LANGUAGE or None)
        effective_prompt = (prompt or "").strip() or (config.ASR_PROMPT or None)
        with GPU_LOCK:
            parsed = self._run(audio, 16000, effective_language, effective_prompt)
        elapsed = time.time() - started

        text = (parsed.get("transcription") or "").strip()
        metrics = {
            "text": text,
            "language": parsed.get("language"),
            "audio_seconds": round(seconds, 2),
            "infer_ms": round(elapsed * 1000, 1),
            "rtf": round(seconds / elapsed, 2) if elapsed > 0 else 0.0,
            "skipped": None,
        }
        log.info("asr ok %.2fs audio → %.2fs infer (rtf=%.2f) lang=%s chars=%d",
                 seconds, elapsed, metrics["rtf"], metrics["language"], len(text))
        return metrics

    # ---------------- 状态 ----------------

    def descriptor(self) -> dict:
        """``/health`` 里的 asr 字段。"""
        return {
            "enabled": self.enabled,
            "loaded": self.ready,
            "model": self.model_path,
            "device": self.device,
            "dtype": self.dtype_name,
            "language": config.ASR_LANGUAGE or None,
            "load_seconds": self.load_seconds,
            "load_error": self.load_error,
            "streaming": False,
        }
