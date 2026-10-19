"""WebSocket 端到端验证：模拟浏览器「按住说话 → 松手 → 面试官回复」全链路。

工作方式与前端 `useVoiceStream` + `pcmRecorder` 完全一致：
1. ``POST /api/mock-interview/start/{projectId}`` 建会话；
2. 连 ``ws://127.0.0.1:8080/api/ws/interview``，发 ``{"type":"start","sessionId":N}``；
3. 按实时速率（100ms 一块）推送 16k/16bit/单声道 PCM；
4. 发 ``{"type":"end"}``，等 ``user_text``（= ASR 结果）与后续 ``text`` 增量。

用法（backend 与 voice-service 都在跑）：
    voice-service/.venv/Scripts/python.exe scripts/e2e_ws_asr.py
"""

from __future__ import annotations

import asyncio
import io
import json
import sys
import time
import urllib.request
import wave

import websockets

BASE = "http://127.0.0.1:8080"
WS = "ws://127.0.0.1:8080/api/ws/interview"
PROJECT_ID = 1
TTS_URL = "http://127.0.0.1:9100/tts"

UTTERANCE = "我在上一家公司负责订单系统的重构，把单体拆成了三个微服务，接口平均延迟从三百毫秒降到了八十毫秒。"


def http_json(url: str, payload: dict | None = None, timeout: int = 180) -> dict:
    data = json.dumps(payload or {}, ensure_ascii=False).encode("utf-8")
    req = urllib.request.Request(url, data=data, method="POST",
                                 headers={"Content-Type": "application/json; charset=utf-8"})
    with urllib.request.urlopen(req, timeout=timeout) as resp:
        return json.loads(resp.read().decode("utf-8"))


def make_speech() -> bytes:
    """用本地 TTS 合成一段面试回答，当作"候选人说话"。"""
    payload = {"text": UTTERANCE, "speaker": "uncle_fu", "language": "Chinese"}
    req = urllib.request.Request(TTS_URL, data=json.dumps(payload, ensure_ascii=False).encode("utf-8"),
                                 headers={"Content-Type": "application/json; charset=utf-8"})
    with urllib.request.urlopen(req, timeout=180) as resp:
        wav_bytes = resp.read()
    with wave.open(io.BytesIO(wav_bytes)) as w:
        assert w.getframerate() == 16000 and w.getnchannels() == 1 and w.getsampwidth() == 2
        pcm = w.readframes(w.getnframes())
        seconds = w.getnframes() / w.getframerate()
    print(f"合成语音 {seconds:.1f}s / {len(pcm)} 字节 PCM")
    return pcm


async def main() -> int:
    print(f"原文: {UTTERANCE}")

    started = http_json(f"{BASE}/api/mock-interview/start/{PROJECT_ID}")
    if started.get("code") != 0:
        print("建会话失败:", started)
        return 1
    session = started["data"]
    session_id = session.get("sessionId") or session.get("id")
    print(f"会话 {session_id} 已创建（开场白 {len(session.get('openingQuestion') or '')} 字）")

    pcm = make_speech()
    chunk = 3200  # 100ms

    events: list[tuple[float, str, dict]] = []
    t0 = time.time()

    async with websockets.connect(WS, max_size=None, ping_interval=None) as ws:
        await ws.send(json.dumps({"type": "start", "sessionId": session_id}))

        # 等 ready（最多 15s）
        try:
            raw = await asyncio.wait_for(ws.recv(), timeout=15)
            events.append((time.time() - t0, "recv", json.loads(raw)))
        except asyncio.TimeoutError:
            print("❌ 15s 内没收到 ready")
            return 1

        # 一边推音频一边收事件（实时速率）
        sent = 0
        while sent < len(pcm):
            await ws.send(pcm[sent:sent + chunk])
            sent += chunk
            await asyncio.sleep(0.1)
            # 非阻塞收一下可能已经到达的 partial
            try:
                raw = await asyncio.wait_for(ws.recv(), timeout=0)
                events.append((time.time() - t0, "recv", json.loads(raw)))
            except (asyncio.TimeoutError, Exception):
                pass
        print(f"音频推送完成 {len(pcm)} 字节")

        await ws.send(json.dumps({"type": "end"}))
        t_end = time.time()

        # 收尾：直到 done / error 或超时
        while True:
            try:
                raw = await asyncio.wait_for(ws.recv(), timeout=25)
            except asyncio.TimeoutError:
                break
            if isinstance(raw, (bytes, bytearray)):
                events.append((time.time() - t0, "audio", {"bytes": len(raw)}))
                continue
            node = json.loads(raw)
            events.append((time.time() - t0, "recv", node))
            if node.get("type") in ("done", "error"):
                break

    # ---------------- 汇总 ----------------
    print("\n== 事件时间线 ==")
    partials = 0
    audio_bytes = 0
    user_text = None
    for at, kind, node in events:
        if kind == "audio":
            audio_bytes += node["bytes"]
            continue
        typ = node.get("type")
        data = node.get("data") or {}
        if typ == "partial":
            partials += 1
            print(f"  {at:6.2f}s partial  #{partials}: {data.get('text')}")
        elif typ == "user_text":
            user_text = data.get("text")
            print(f"  {at:6.2f}s user_text: {user_text}")
        elif typ == "text":
            print(f"  {at:6.2f}s text     +{data.get('delta')!r}")
        elif typ == "ready":
            print(f"  {at:6.2f}s ready    engine={data.get('engine')}")
        else:
            brief = {k: v for k, v in data.items() if k in ("message", "userMessage", "sessionId")}
            print(f"  {at:6.2f}s {typ:9s} {json.dumps(brief, ensure_ascii=False)[:160]}")

    print("\n== 结果 ==")
    print(f"中间结果 {partials} 次；面试官语音 {audio_bytes} 字节")
    latency = None
    for at, kind, node in events:
        if kind == "recv" and node.get("type") == "user_text":
            latency = at - (t_end - t0)
            break
    if latency is not None:
        print(f"松手 → 识别文本就绪: {latency:.2f}s")
    ok = user_text is not None and len(user_text) > 10
    print(f"识别文本: {user_text}")
    print("✅ 本地 ASR 全链路打通" if ok else "❌ 未拿到识别文本")
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(asyncio.run(main()))
