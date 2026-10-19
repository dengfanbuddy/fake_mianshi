"""诊断 TTS 流式输出里的「咔哒 / 刺耳噪音」。

方法：绕过 HTTP 库，直接读 socket 并**手工解析 chunked 编码**，这样就能拿到
/tts/stream 每一块的真实边界（一个 HTTP chunk == 一次 yield == 一个模型分块）。

然后对每个接缝测量：
- ``step``     = |本块第一个样本 - 上一块最后一个样本|，硬阶跃就是爆音；
- ``head_max`` = 本块前 5ms 的峰值，声码器重解码造成的瞬态会体现在这里；
- 两者都跟「本块内部正常的 |Δx| 分布」比较，看是不是离群点。

用法（sidecar 需已在跑）：
    set PYTHONIOENCODING=utf-8 && .venv/Scripts/python.exe scripts/diagnose_tts_clicks.py
    .venv/Scripts/python.exe scripts/diagnose_tts_clicks.py --chunk-size 32
"""

from __future__ import annotations

import argparse
import json
import socket
import statistics
import sys

import numpy as np

DEFAULT_URL = "http://127.0.0.1:9100"
TEXT = "这是一段用于诊断爆音的中文语音，请仔细听一下中间有没有咔哒声。"


class RawChunkedResponse:
    """极简 HTTP/1.1 客户端：手工解析 chunked 编码，保留服务端每次 yield 的边界。"""

    def __init__(self, host: str, port: int, path: str, payload: bytes, timeout: float):
        self.sock = socket.create_connection((host, port), timeout=timeout)
        head = (
            f"POST {path} HTTP/1.1\r\n"
            f"Host: {host}:{port}\r\n"
            "Content-Type: application/json; charset=utf-8\r\n"
            f"Content-Length: {len(payload)}\r\n"
            "Connection: close\r\n\r\n"
        ).encode("ascii")
        self.sock.sendall(head + payload)
        self.buf = b""

    def _fill(self) -> bool:
        data = self.sock.recv(65536)
        if not data:
            return False
        self.buf += data
        return True

    def _read_line(self) -> bytes:
        while b"\r\n" not in self.buf:
            if not self._fill():
                raise EOFError("连接提前关闭")
        line, self.buf = self.buf.split(b"\r\n", 1)
        return line

    def _read_exact(self, n: int) -> bytes:
        while len(self.buf) < n:
            if not self._fill():
                raise EOFError(f"还差 {n - len(self.buf)} 字节时连接关闭")
        out, self.buf = self.buf[:n], self.buf[n:]
        return out

    def headers(self) -> dict[str, str]:
        status = self._read_line().decode("latin-1")
        if " 200 " not in status:
            raise SystemExit(f"sidecar 返回异常: {status}")
        headers: dict[str, str] = {}
        while True:
            line = self._read_line()
            if not line:
                return headers
            k, _, v = line.decode("latin-1").partition(":")
            headers[k.strip().lower()] = v.strip()

    def chunks(self):
        while True:
            size_line = self._read_line().split(b";")[0].strip()
            size = int(size_line, 16)
            if size == 0:
                return
            yield self._read_exact(size)
            self._read_exact(2)  # 尾部 CRLF


def analyze(chunks: list[np.ndarray], sr: int) -> None:
    total = sum(c.size for c in chunks)
    print(f"服务端分块数={len(chunks)}  样本总数={total}  时长={total / sr:.2f}s")
    print("每块样本数:", [int(c.size) for c in chunks][:10], "…" if len(chunks) > 10 else "")

    steps: list[float] = []
    heads: list[float] = []
    inner: list[float] = []
    for i in range(1, len(chunks)):
        prev, cur = chunks[i - 1], chunks[i]
        if prev.size == 0 or cur.size == 0:
            continue
        steps.append(abs(float(cur[0]) - float(prev[-1])))
        head_n = max(1, int(sr * 0.005))
        heads.append(float(np.max(np.abs(cur[:head_n]))))
        d = np.abs(np.diff(cur))
        inner.extend(np.percentile(d, [50, 99.9]).tolist())

    if not steps:
        print("只有一个分块，没有接缝可测（chunk_size 太大或文本太短）")
        return

    step_med = statistics.median(steps)
    step_max = max(steps)
    inner_med = statistics.median(inner[0::2]) if inner else 0.0
    inner_p999 = statistics.median(inner[1::2]) if len(inner) > 1 else 0.0
    head_med = statistics.median(heads)
    head_max = max(heads)

    print(f"\n接缝数={len(steps)}")
    print(f"  接缝阶跃 step     中位数={step_med:.4f}  最大={step_max:.4f}")
    print(f"  块内 |Δx|         中位数={inner_med:.4f}  99.9分位={inner_p999:.4f}")
    print(f"  块首 5ms 峰值     中位数={head_med:.4f}  最大={head_max:.4f}")
    ratio = step_med / inner_p999 if inner_p999 > 0 else float("inf")
    print(f"  接缝阶跃 / 块内99.9分位 = {ratio:.2f}  (>1 说明接缝比块内任何位置都更跳)")

    if ratio > 1.0 or step_max > 0.5:
        print("=> 结论：接缝处存在硬阶跃，是刺耳噪音的来源（需在拼块处做交叉淡化）")
    elif step_med > inner_med * 3:
        print("=> 结论：接缝有轻微不连续，可能有隐约的咔哒")
    else:
        print("=> 结论：接缝连续，噪音不来自分块拼接，需查播放端（AudioContext 重采样）")


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--base", default=DEFAULT_URL)
    ap.add_argument("--chunk-size", type=int, default=4)
    ap.add_argument("--text", default=TEXT)
    args = ap.parse_args()

    host_port, _, _ = args.base.replace("http://", "").partition("/")
    host, _, port = host_port.partition(":")
    payload = json.dumps(
        {"text": args.text, "speaker": "uncle_fu", "language": "Chinese",
         "chunk_size": args.chunk_size},
        ensure_ascii=False,
    ).encode("utf-8")

    resp = RawChunkedResponse(host, int(port or 9100), "/tts/stream", payload, timeout=600)
    headers = resp.headers()
    sr = int(headers.get("x-sample-rate", "16000"))
    print(f"采样率={sr}  chunk_size={args.chunk_size}")

    chunks: list[np.ndarray] = []
    for raw in resp.chunks():
        if len(raw) % 2:
            print(f"!! 收到奇数长度分块 {len(raw)} 字节 —— 前端 new Int16Array 会抛 RangeError")
            raw = raw[:-1]
        if raw:
            chunks.append(np.frombuffer(raw, dtype="<i2").astype(np.float32) / 32768.0)

    audio = np.concatenate(chunks) if chunks else np.zeros(0, dtype=np.float32)
    if audio.size:
        print(f"整体 peak={float(np.max(np.abs(audio))):.4f} "
              f"RMS={float(np.sqrt(np.mean(audio ** 2))):.4f} "
              f"DC={float(np.mean(audio)):+.5f}")
    analyze(chunks, sr)
    return 0


if __name__ == "__main__":
    sys.exit(main())
