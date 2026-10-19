"""自检脚本：加载模型、合成一句中文，打印延迟/RTF/显存占用，并落盘 wav 供人工试听。

用法：
    uv run python selfcheck.py                     # 默认输出 selfcheck_out.wav
    uv run python selfcheck.py --text "自定义文本" --speaker Serena
"""

from __future__ import annotations

import argparse
import time

from app import config
from app.main import _wav_header
from app.tts_engine import TtsEngine


def gpu_mem() -> str:
    try:
        import torch

        if not torch.cuda.is_available():
            return "CUDA 不可用"
        allocated = torch.cuda.memory_allocated() / 1024 ** 3
        reserved = torch.cuda.memory_reserved() / 1024 ** 3
        total = torch.cuda.get_device_properties(0).total_memory / 1024 ** 3
        return f"显存 已分配 {allocated:.2f}GB / 保留 {reserved:.2f}GB / 总 {total:.2f}GB"
    except Exception as exc:  # noqa: BLE001
        return f"显存查询失败: {exc}"


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--text", default="你好，我是今天的面试官，我们先聊一下你最近做的项目吧。")
    parser.add_argument("--speaker", default="uncle_fu")
    parser.add_argument("--instruct", default="冷静、专业的技术面试官，语速中等")
    parser.add_argument("--out", default="selfcheck_out.wav")
    args = parser.parse_args()

    print(f"模型: {config.MODEL_PATH}  设备: {config.DEVICE}  后端: {config.BACKEND}")

    engine = TtsEngine()
    t0 = time.time()
    engine.ensure_loaded()
    print(f"加载耗时: {time.time() - t0:.1f}s  kind={engine.kind}  speakers={engine.speakers}")
    print(gpu_mem())

    # 首次（含 CUDA graph 捕获）与稳态各测一次
    for label in ("首次", "稳态"):
        audio, metrics = engine.synth(args.text, args.speaker, args.instruct, None)
        print(f"[{label}] {metrics}  {gpu_mem()}")

    pcm = engine.to_pcm16(audio)
    with open(args.out, "wb") as fp:
        fp.write(_wav_header(len(pcm)) + pcm)
    print(f"已写出 {args.out}（{config.OUTPUT_SAMPLE_RATE}Hz，{len(pcm) / 32000:.2f}s）")


if __name__ == "__main__":
    main()
