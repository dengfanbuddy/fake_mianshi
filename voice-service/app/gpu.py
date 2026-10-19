"""进程内共享的 GPU 串行锁。

sidecar 里同时驻留 Qwen3-TTS 和 Qwen3-ASR 两张模型，共用同一块显卡。
两个引擎的 ``generate`` 调用必须串行：并发不会算错（CUDA 会时间片调度），
但两张模型同时生成会在显存峰值上叠加，16GB 卡有 OOM 风险，且互相拖慢首包延迟。

统一放在这里而不是各自模块内，避免出现两把互不相干的锁（那等于没加锁）。
"""

from __future__ import annotations

import threading

GPU_LOCK = threading.Lock()
