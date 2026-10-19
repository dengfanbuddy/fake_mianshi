"""Qwen3-ASR 本机基准（走 sidecar 的 HTTP 接口，和生产路径完全一致）。

流程是「本地 TTS 合成 → 立刻用本地 ASR 识别 → 与原文比对」的闭环：
既测了延迟，也测了在真实合成语音上的准确率（比公开测试集更贴近面试场景）。

前置：voice-service 已启动（默认 127.0.0.1:9100）。
用法：voice-service/.venv/Scripts/python.exe scripts/bench_asr.py [--base http://127.0.0.1:9100]
"""

from __future__ import annotations

import argparse
import io
import json
import time
import urllib.parse
import urllib.request

import soundfile as sf

CASES = [
    "我在上一家公司负责订单系统的重构，把单体拆成了三个微服务，接口平均延迟从三百毫秒降到了八十毫秒。",
    "请你先做一个简单的自我介绍，重点讲一下你最近做过的项目，以及你在里面承担的角色。",
    "缓存穿透可以用布隆过滤器挡住不存在的键，再配合空值缓存和互斥锁，防止请求全部打到数据库。",
    "这个岗位要求熟悉 Kube­rnetes 和分布式事务，你能说说 TCC 和 Saga 的区别吗？",
]


def post(url: str, data: bytes, content_type: str, extra: dict | None = None,
         timeout: int = 180) -> tuple[bytes, dict]:
    headers = {"Content-Type": content_type}
    headers.update(extra or {})
    req = urllib.request.Request(url, data=data, headers=headers)
    started = time.time()
    with urllib.request.urlopen(req, timeout=timeout) as resp:
        body = resp.read()
        return body, {"elapsed": time.time() - started,
                      "headers": {k.lower(): v for k, v in resp.headers.items()}}


def tts(base: str, text: str) -> bytes:
    payload = json.dumps({"text": text, "speaker": "uncle_fu", "language": "Chinese"},
                         ensure_ascii=False).encode("utf-8")
    body, _ = post(base + "/tts", payload, "application/json; charset=utf-8")
    return body


def asr(base: str, wav: bytes, language: str | None = None) -> dict:
    query = f"?language={urllib.parse.quote(language)}" if language else ""
    body, meta = post(base + "/asr" + query, wav, "audio/wav")
    result = json.loads(body.decode("utf-8"))
    result["wall_ms"] = round(meta["elapsed"] * 1000, 1)
    return result


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--base", default="http://127.0.0.1:9100")
    args = parser.parse_args()

    health = json.loads(urllib.request.urlopen(args.base + "/health", timeout=10).read())
    print(f"sidecar {args.base}")
    print(f"  TTS {health['model']} loaded={health['loaded']} kind={health['kind']}")
    print(f"  ASR {health['asr']['model']} loaded={health['asr']['loaded']} "
          f"load={health['asr']['load_seconds']}s")
    if not health["asr"]["loaded"]:
        raise SystemExit("ASR 未加载，先 POST /asr/warmup")

    total_audio = total_infer = 0.0
    exact = 0
    print("\n== TTS → ASR 闭环 ==")
    for expect in CASES:
        wav = tts(args.base, expect)
        info = sf.info(io.BytesIO(wav))
        result = asr(args.base, wav)
        text = result["text"].strip()
        total_audio += result["audio_seconds"]
        total_infer += result["infer_ms"] / 1000
        same = text.strip("，。？ ") == expect.strip("，。？ ")
        exact += int(same)
        print(f"\n  原文({info.duration:.1f}s): {expect}")
        print(f"  识别: {text}")
        print(f"  语种={result['language']} 音频={result['audio_seconds']}s "
              f"推理={result['infer_ms']}ms 端到端={result['wall_ms']}ms RTF={result['rtf']} "
              f"{'✅一致' if same else '❌有差异'}")

    print(f"\n== 汇总 == 用例 {len(CASES)} 条，逐字一致 {exact}/{len(CASES)}；"
          f"音频 {total_audio:.2f}s / 推理 {total_infer:.2f}s → {total_audio / total_infer:.2f}x 实时")


if __name__ == "__main__":
    main()
