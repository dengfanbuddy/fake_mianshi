"""voice-service：本地 Qwen3 语音合成 + 语音识别服务（FastAPI）。

接口约定（供 Spring Boot 后端 LocalQwenVoiceService / LocalAsrSession 调用）：

- ``GET  /health``      → TTS/ASR 两张模型的状态、可用音色、采样率、加载耗时
- ``GET  /speakers``    → 可用音色列表
- ``POST /tts``         → 返回 16k/16bit/单声道 wav（对齐前端 PcmQueuePlayer 的 16k 假设）
- ``POST /tts/stream``  → 返回裸 PCM（16k/16bit/单声道）分块响应
- ``POST /warmup``      → 手动触发 TTS 加载/warmup
- ``POST /asr``         → 识别请求体音频（wav 或裸 PCM16），返回 JSON 文本
- ``POST /asr/warmup``  → 手动触发 ASR 加载/warmup

所有响应带 ``X-TTFA-Ms`` / ``X-Rtf`` 等头，便于后端日志里观测延迟。
"""

from __future__ import annotations

import io
import logging
import os
import struct
import threading
from contextlib import asynccontextmanager
from typing import Iterator

from fastapi import FastAPI, HTTPException, Request
from fastapi.responses import JSONResponse, Response, StreamingResponse
from pydantic import BaseModel, Field
from starlette.concurrency import run_in_threadpool

from . import config
from .asr_engine import AsrEngine, decode_audio
from .tts_engine import TtsEngine

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s %(levelname)s [%(name)s] %(message)s",
)
log = logging.getLogger("voice-service")

engine = TtsEngine()
asr = AsrEngine()


@asynccontextmanager
async def lifespan(_: FastAPI):
    log.info("voice-service 启动: host=%s port=%s", config.HOST, config.PORT)
    log.info("  TTS model=%s", config.MODEL_PATH)
    log.info("  ASR model=%s enabled=%s", config.ASR_MODEL, config.ASR_ENABLED)
    if config.PRELOAD or (config.ASR_ENABLED and config.ASR_PRELOAD):
        threading.Thread(target=_preload_all, name="preload", daemon=True).start()
    yield


app = FastAPI(title="voice-service", version="0.1.0", lifespan=lifespan)


class TtsRequest(BaseModel):
    text: str = Field(..., description="待合成文本")
    speaker: str | None = Field(None, description="音色名，如 Serena / Uncle_Fu")
    instruct: str | None = Field(None, description="自然语言风格指令，如：语气严肃、语速偏快")
    language: str | None = Field(None, description="Chinese / English / Auto")
    chunk_size: int | None = Field(None, description="流式分块步数，默认取配置")


def _validate(req: TtsRequest) -> str:
    text = (req.text or "").strip()
    if not text:
        raise HTTPException(status_code=400, detail="text 不能为空")
    if len(text) > config.MAX_TEXT_CHARS:
        raise HTTPException(
            status_code=400,
            detail=f"text 超长（{len(text)} > {config.MAX_TEXT_CHARS}），请按句切分后调用",
        )
    return text


def _wav_header(pcm_len: int, sample_rate: int = None, channels: int = 1, bits: int = 16) -> bytes:
    sample_rate = sample_rate or config.OUTPUT_SAMPLE_RATE
    byte_rate = sample_rate * channels * bits // 8
    return (
        b"RIFF"
        + struct.pack("<I", 36 + pcm_len)
        + b"WAVEfmt "
        + struct.pack("<IHHIIHH", 16, 1, channels, sample_rate, byte_rate, channels * bits // 8, bits)
        + b"data"
        + struct.pack("<I", pcm_len)
    )


@app.get("/health")
def health() -> JSONResponse:
    return JSONResponse(
        {
            "status": "ok" if engine.ready else ("error" if engine.load_error else "loading"),
            "kind": engine.kind,
            "model": engine.model_path,
            "device": config.DEVICE,
            "backend": config.BACKEND,
            "sample_rate": config.OUTPUT_SAMPLE_RATE,
            "speakers": engine.speakers,
            "loaded": engine.ready,
            "load_seconds": engine.load_seconds,
            "load_error": engine.load_error,
            "asr": asr.descriptor(),
            # 后端结束 sidecar 时要知道"真正占显存的进程是谁"：
            # .venv/Scripts/python.exe 只是启动器，它会再拉起 base 解释器，两者 PID 不同。
            # cwd 用于判定"这个在监听 9100 的实例就是我自己的 voice-service"（pid 文件丢失后的兜底）。
            "pid": os.getpid(),
            "cwd": os.getcwd(),
        }
    )


@app.get("/speakers")
def speakers() -> JSONResponse:
    return JSONResponse({"speakers": engine.speakers})


@app.post("/warmup")
def warmup() -> JSONResponse:
    try:
        engine.ensure_loaded()
    except Exception as exc:  # noqa: BLE001
        raise HTTPException(status_code=503, detail=f"模型加载失败: {exc}") from exc
    return JSONResponse({"loaded": True, "kind": engine.kind, "speakers": engine.speakers})


@app.post("/tts")
def tts(req: TtsRequest) -> Response:
    text = _validate(req)
    try:
        audio, metrics = engine.synth(text, req.speaker, req.instruct, req.language)
    except HTTPException:
        raise
    except Exception as exc:  # noqa: BLE001
        log.exception("合成失败")
        raise HTTPException(status_code=500, detail=f"合成失败: {exc}") from exc

    pcm = engine.to_pcm16(audio)
    if not pcm:
        raise HTTPException(status_code=500, detail="合成结果为空")
    body = _wav_header(len(pcm)) + pcm
    log.info("tts ok chars=%d %s", len(text), metrics)
    return Response(
        content=body,
        media_type="audio/wav",
        headers={
            "X-TTFA-Ms": str(metrics["ttfa_ms"]),
            "X-Rtf": str(metrics["rtf"]),
            "X-Audio-Seconds": str(metrics["audio_seconds"]),
            "X-Sample-Rate": str(config.OUTPUT_SAMPLE_RATE),
        },
    )


@app.post("/tts/stream")
def tts_stream(req: TtsRequest) -> StreamingResponse:
    text = _validate(req)

    def pcm_iter() -> Iterator[bytes]:
        try:
            total = 0
            for chunk in engine.synth_stream(text, req.speaker, req.instruct, req.language,
                                            req.chunk_size):
                data = engine.to_pcm16(chunk)
                if data:
                    total += len(data)
                    yield data
            log.info("tts/stream done chars=%d pcm_bytes=%d", len(text), total)
        except Exception as exc:  # noqa: BLE001 - 流已开始，只能中断
            log.exception("流式合成失败: %s", exc)
            raise

    return StreamingResponse(
        pcm_iter(),
        media_type="application/octet-stream",
        headers={
            "X-Sample-Rate": str(config.OUTPUT_SAMPLE_RATE),
            "X-Channels": "1",
            "X-Bits": "16",
            "Cache-Control": "no-store",
        },
    )


def _preload_all() -> None:
    """后台**串行**加载两张模型（顺序很重要，见 lifespan 里的注释）。"""
    if config.PRELOAD:
        _preload()
    if config.ASR_ENABLED and config.ASR_PRELOAD:
        _preload_asr()


def _preload() -> None:
    try:
        engine.ensure_loaded()
    except Exception:  # noqa: BLE001 - 已在 ensure_loaded 内记录
        log.error("预加载失败，/health 会返回 error，可修复环境后 POST /warmup 重试")


def _preload_asr() -> None:
    try:
        asr.ensure_loaded()
    except Exception:  # noqa: BLE001 - 已在 ensure_loaded 内记录
        log.error("ASR 预加载失败，/health 的 asr.load_error 会给出原因，可 POST /asr/warmup 重试")


# ---------------- 语音识别 ----------------

@app.post("/asr/warmup")
def asr_warmup() -> JSONResponse:
    if not asr.enabled:
        raise HTTPException(status_code=503, detail="本地 ASR 未启用（VOICE_ASR_ENABLED=0）")
    try:
        asr.ensure_loaded()
    except Exception as exc:  # noqa: BLE001
        raise HTTPException(status_code=503, detail=f"ASR 模型加载失败: {exc}") from exc
    return JSONResponse(asr.descriptor())


@app.post("/asr")
async def asr_transcribe(request: Request, language: str | None = None,
                         prompt: str | None = None) -> Response:
    """识别请求体里的音频。

    请求体二选一：
    - ``Content-Type: audio/wav``（或任何 soundfile 认得格式）→ 采样率以文件头为准；
    - ``Content-Type: application/octet-stream`` 裸 PCM16LE 单声道 → 采样率取
      ``X-Sample-Rate`` 头，缺省 16000。

    响应 JSON：``{text, language, audio_seconds, infer_ms, rtf, skipped}``。
    """
    if not asr.enabled:
        raise HTTPException(status_code=503, detail="本地 ASR 未启用（VOICE_ASR_ENABLED=0）")
    raw = await request.body()
    if not raw:
        raise HTTPException(status_code=400, detail="请求体为空：需要 wav 或裸 PCM16 音频")

    try:
        sample_rate = int(request.headers.get("x-sample-rate") or config.OUTPUT_SAMPLE_RATE)
    except ValueError:
        sample_rate = config.OUTPUT_SAMPLE_RATE

    def work() -> dict:
        # 先解析一次，拿到真实采样率（wav 头里的优先），再把解码后的数组交给引擎
        audio, sr = decode_audio(raw, sample_rate)
        return asr.transcribe(audio, sample_rate=sr, language=language, prompt=prompt)

    try:
        # 转写是 CPU/GPU 阻塞操作，放线程池，别卡住事件循环（否则并发 TTS 会被拖住）
        result = await run_in_threadpool(work)
    except HTTPException:
        raise
    except Exception as exc:  # noqa: BLE001
        log.exception("识别失败")
        raise HTTPException(status_code=500, detail=f"识别失败: {exc}") from exc

    return JSONResponse(
        result,
        headers={
            "X-Infer-Ms": str(result["infer_ms"]),
            "X-Rtf": str(result["rtf"]),
            "X-Audio-Seconds": str(result["audio_seconds"]),
            "X-Language": str(result.get("language") or ""),
        },
    )
