"""Qwen3-TTS 推理封装。

设计要点：
- 优先用 faster-qwen3-tts（CUDA graphs + 真流式，RTF 远高于官方包）；
- 若环境里只有官方 qwen-tts，自动降级为非流式（一次性产出整句音频）；
- 模型不是线程安全的，所有生成调用串行化（_GPU_LOCK），避免多请求把显存打爆。
"""

from __future__ import annotations

import inspect
import logging
import threading
import time
from typing import Any, Iterator

import numpy as np

from . import config
from .gpu import GPU_LOCK as _GPU_LOCK

log = logging.getLogger("voice-service.tts")


def _to_numpy(audio: Any) -> np.ndarray:
    """把 torch tensor / list / np 数组统一成 1-D float32 numpy。"""
    if hasattr(audio, "detach"):
        audio = audio.detach().to("cpu").float().numpy()
    arr = np.asarray(audio, dtype=np.float32)
    if arr.ndim > 1:
        arr = arr.reshape(-1)
    return arr


def _resample(audio: np.ndarray, src_rate: int, dst_rate: int) -> np.ndarray:
    """重采样（soxr HQ，缺失时退化为线性插值）。"""
    if src_rate == dst_rate or audio.size == 0:
        return audio
    try:
        import soxr

        return np.asarray(soxr.resample(audio, src_rate, dst_rate, quality="HQ"), dtype=np.float32)
    except Exception:  # pragma: no cover - 兜底路径
        n_dst = int(round(audio.size * dst_rate / src_rate))
        if n_dst <= 1:
            return audio
        x_src = np.linspace(0.0, 1.0, audio.size, dtype=np.float64)
        x_dst = np.linspace(0.0, 1.0, n_dst, dtype=np.float64)
        return np.interp(x_dst, x_src, audio).astype(np.float32)


def _call_with_supported_kwargs(fn: Any, kwargs: dict[str, Any]) -> Any:
    """按目标函数签名过滤 kwargs，兼容不同版本的 API。

    只丢弃「值为 None 且有默认值」的可选参数；必填参数即使为 None 也保留，
    让真实错误暴露出来（而不是变成 "missing positional argument"）。
    """
    try:
        sig = inspect.signature(fn)
    except (TypeError, ValueError):
        return fn(**kwargs)
    if any(p.kind is inspect.Parameter.VAR_KEYWORD for p in sig.parameters.values()):
        return fn(**kwargs)
    allowed: dict[str, Any] = {}
    for key, value in kwargs.items():
        param = sig.parameters.get(key)
        if param is None:
            continue
        if value is None and param.default is not inspect.Parameter.empty:
            continue
        allowed[key] = value
    return fn(**allowed)


class TtsEngine:
    """懒加载的 Qwen3-TTS 引擎。"""

    def __init__(self) -> None:
        self.model: Any = None
        self.kind = "none"  # faster | official | none
        self.model_path = config.MODEL_PATH
        self.load_error: str | None = None
        self.load_seconds: float | None = None
        self._speakers: list[str] = []
        self._lock = threading.Lock()

    # ---------------- 加载 ----------------

    @property
    def ready(self) -> bool:
        return self.model is not None

    def ensure_loaded(self) -> None:
        if self.ready:
            return
        with self._lock:
            if self.ready:
                return
            started = time.time()
            try:
                self._load()
                self.load_error = None
            except Exception as exc:  # noqa: BLE001 - 需要把失败原因暴露给 /health
                self.load_error = f"{type(exc).__name__}: {exc}"
                log.exception("模型加载失败: %s", self.load_error)
                raise
            finally:
                self.load_seconds = round(time.time() - started, 1)

    def _load(self) -> None:
        try:
            from faster_qwen3_tts import FasterQwen3TTS  # type: ignore

            log.info("加载 faster-qwen3-tts: %s (backend=%s device=%s)",
                     self.model_path, config.BACKEND, config.DEVICE)
            self.model = self._load_faster(FasterQwen3TTS)
            self.kind = "faster"
        except ImportError as exc:
            # 注意：这里必须把异常打出来。曾经出现过 ASR 侧并发导入 torch/transformers
            # 与本模块导入撞车导致 ImportError，然后静默降级成官方 backend，
            # TTS 的 RTF 从 ~2.0 掉到 ~0.3，而日志里只有一句"未安装"，极难排查。
            log.warning("faster-qwen3-tts 不可用，降级为官方 qwen-tts（无流式，慢约 5-7 倍）: %r", exc)
            from qwen_tts import Qwen3TTSModel  # type: ignore

            self.model = Qwen3TTSModel.from_pretrained(
                self.model_path, device_map=config.DEVICE, dtype="bfloat16"
            )
            self.kind = "official"

        self._speakers = self._detect_speakers()
        self._warmup()
        log.info("模型就绪: kind=%s speakers=%s 加载耗时=%.1fs",
                 self.kind, self._speakers, self.load_seconds or 0)

    def _load_faster(self, cls: Any) -> Any:
        """faster-qwen3-tts 的 from_pretrained 参数在不同版本间略有差异，逐个尝试。"""
        attempts = [
            {"backend": config.BACKEND, "device": config.DEVICE},
            {"device": config.DEVICE},
            {},
        ]
        last_exc: Exception | None = None
        for kwargs in attempts:
            try:
                return cls.from_pretrained(self.model_path, **kwargs)
            except TypeError as exc:  # 参数不支持
                last_exc = exc
            except Exception as exc:  # 其他错误直接抛，避免掩盖真实问题
                if "backend" in kwargs and "ggml" in str(exc).lower():
                    last_exc = exc
                    continue
                raise
        raise last_exc if last_exc else RuntimeError("from_pretrained 失败")

    def _warmup(self) -> None:
        fn = getattr(self.model, "warmup", None)
        if fn is None:
            return
        try:
            with _GPU_LOCK:
                _call_with_supported_kwargs(fn, {"prefill_len": 100})
            log.info("warmup 完成（CUDA graphs 已捕获）")
        except Exception as exc:  # noqa: BLE001 - warmup 失败不影响可用性
            log.warning("warmup 失败（可忽略）: %s", exc)

    def _detect_speakers(self) -> list[str]:
        """音色列表在内部 model 上（wrapper 只做推理加速），两处都找一遍。"""
        for host in (self.model, getattr(self.model, "model", None)):
            if host is None:
                continue
            for name in ("get_supported_speakers", "list_speakers", "supported_speakers"):
                attr = getattr(host, name, None)
                if attr is None:
                    continue
                try:
                    value = attr() if callable(attr) else attr
                    if value:
                        return [str(v) for v in value]
                except Exception:  # noqa: BLE001
                    continue
        return []

    @property
    def speakers(self) -> list[str]:
        return self._speakers

    # ---------------- 合成 ----------------

    def _generate_kwargs(self, text: str, speaker: str | None, instruct: str | None,
                         language: str | None) -> dict[str, Any]:
        # CustomVoice 的 speaker 是必填项；未指定时用第一个可用音色兜底
        if not speaker:
            speaker = self._speakers[0] if self._speakers else "serena"
        return {
            "text": text,
            "language": language or config.DEFAULT_LANGUAGE,
            "speaker": speaker,
            "instruct": instruct,
        }

    def _stream_fn(self) -> Any:
        for name in ("generate_custom_voice_streaming", "generate_voice_clone_streaming"):
            fn = getattr(self.model, name, None)
            if fn is not None:
                return fn
        return None

    def _full_fn(self) -> Any:
        for name in ("generate_custom_voice", "generate_voice_clone"):
            fn = getattr(self.model, name, None)
            if fn is not None:
                return fn
        raise RuntimeError("模型不支持 generate_custom_voice / generate_custom_voice_streaming")

    def synth_stream(self, text: str, speaker: str | None, instruct: str | None,
                     language: str | None, chunk_size: int | None = None) -> Iterator[np.ndarray]:
        """流式合成：逐步 yield 已重采样到目标采样率的 float32 音频块。"""
        self.ensure_loaded()
        src_rate_holder = {"sr": 24000}
        target = config.OUTPUT_SAMPLE_RATE

        with _GPU_LOCK:
            fn = self._stream_fn()
            if fn is None:
                # 降级：一次性生成整段再切块返回
                audio = self._synth_locked(text, speaker, instruct, language,
                                           src_rate_holder=src_rate_holder)
                step = max(1, int(target * 0.1))  # 100ms 一块
                for i in range(0, audio.size, step):
                    yield audio[i:i + step]
                return

            kwargs = self._generate_kwargs(text, speaker, instruct, language)
            kwargs["chunk_size"] = chunk_size or config.CHUNK_SIZE
            for raw in _call_with_supported_kwargs(fn, kwargs):
                sr = src_rate_holder["sr"]
                if isinstance(raw, tuple):
                    # (audio, sample_rate, timing?) 形态
                    audio = _to_numpy(raw[0])
                    for item in raw[1:]:
                        if isinstance(item, (int, float)) and 4000 <= float(item) <= 96000:
                            sr = int(item)
                            break
                else:
                    audio = _to_numpy(raw)
                src_rate_holder["sr"] = sr
                if audio.size == 0:
                    continue
                yield _resample(audio, sr, target)

    def _synth_locked(self, text: str, speaker: str | None, instruct: str | None,
                      language: str | None, src_rate_holder: dict[str, int]) -> np.ndarray:
        """调用方已持有 _GPU_LOCK。"""
        fn = self._full_fn()
        kwargs = self._generate_kwargs(text, speaker, instruct, language)
        result = _call_with_supported_kwargs(fn, kwargs)
        sr = src_rate_holder["sr"]
        if isinstance(result, tuple):
            wavs, sr = result[0], int(result[1])
            audio = _to_numpy(wavs[0] if isinstance(wavs, (list, tuple)) else wavs)
        else:
            audio = _to_numpy(result)
        src_rate_holder["sr"] = sr
        return _resample(audio, sr, config.OUTPUT_SAMPLE_RATE)

    def synth(self, text: str, speaker: str | None, instruct: str | None,
              language: str | None) -> tuple[np.ndarray, dict[str, float]]:
        """非流式合成：返回 (16k float32 音频, 性能指标)。"""
        self.ensure_loaded()
        started = time.time()
        chunks: list[np.ndarray] = []
        ttfa: float | None = None
        for chunk in self.synth_stream(text, speaker, instruct, language):
            if ttfa is None:
                ttfa = time.time() - started
            chunks.append(chunk)
        audio = np.concatenate(chunks) if chunks else np.zeros(0, dtype=np.float32)
        elapsed = time.time() - started
        duration = audio.size / float(config.OUTPUT_SAMPLE_RATE) if audio.size else 0.0
        metrics = {
            "ttfa_ms": round((ttfa or elapsed) * 1000, 1),
            "total_ms": round(elapsed * 1000, 1),
            "audio_seconds": round(duration, 2),
            "rtf": round(duration / elapsed, 2) if elapsed > 0 else 0.0,
        }
        return audio, metrics

    def to_pcm16(self, audio: np.ndarray) -> bytes:
        clipped = np.clip(audio, -1.0, 1.0)
        return (clipped * 32767.0).astype("<i2").tobytes()
