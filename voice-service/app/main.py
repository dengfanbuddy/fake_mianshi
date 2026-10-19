"""voice-service：本地 Qwen3-TTS 合成服务（FastAPI）。

接口约定（供 Spring Boot 后端 LocalQwenVoiceService 调用）：

- ``GET  /health``      → 模型状态、可用音色、采样率、加载耗时
- ``GET  /speakers``    → 可用音色列表
- ``POST /tts``         → 返回 16k/16bit/单声道 wav（对齐前端 PcmQueuePlayer 的 16k 假设）
- ``POST /tts/stream``  → 返回裸 PCM（16k/16bit/单声道）分块响应
- ``POST /warmup``      → 手动触发加载/warmup

所有响应带 ``X-TTFA-Ms`` / ``X-Rtf`` 等头，便于后端日志里观测延迟。
"""

from __future__ import annotations

import io
import logging
import struct
import threading
from contextlib import asynccontextmanager
from typing import Iterator

from fastapi import FastAPI, HTTPException
from fastapi.responses import JSONResponse, Response, StreamingResponse
from pydantic import BaseModel, Field

from . import config
from .tts_engine import TtsEngine

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s %(levelname)s [%(name)s] %(message)s",
)
log = logging.getLogger("voice-service")

engine = TtsEngine()


@asynccontextmanager
async def lifespan(_: FastAPI):
    log.info("voice-service 启动: host=%s port=%s model=%s", config.HOST, config.PORT,
             config.MODEL_PATH)
    if config.PRELOAD:
        # 后台线程加载，接口立即可用（/health 会返回 loading）
        threading.Thread(target=_preload, name="preload", daemon=True).start()
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


def _preload() -> None:
    try:
        engine.ensure_loaded()
    except Exception:  # noqa: BLE001 - 已在 ensure_loaded 内记录
        log.error("预加载失败，/health 会返回 error，可修复环境后 POST /warmup 重试")
