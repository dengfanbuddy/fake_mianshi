import { pcmToWavBlob } from './audio'

/**
 * 16k 单声道 PCM 录音器（基于 AudioContext + ScriptProcessorNode）。
 * 相比 MediaRecorder，直接产出 16k Int16 PCM，便于流式推给实时 ASR，也便于回退封装 WAV 走 HTTP。
 */
export class PcmRecorder {
  constructor({ sampleRate = 16000, onChunk, onError } = {}) {
    this.sampleRate = sampleRate
    this.onChunk = onChunk
    this.onError = onError
    this.ctx = null
    this.source = null
    this.processor = null
    this.stream = null
    this.recording = false
    this.accum = [] // 累积的 Int16 分片，供回退封装 WAV
  }

  async start() {
    try {
      this.stream = await navigator.mediaDevices.getUserMedia({
        audio: {
          channelCount: 1,
          echoCancellation: true,
          noiseSuppression: true,
          autoGainControl: true,
        },
      })
    } catch (e) {
      if (this.onError) this.onError(e)
      throw e
    }
    this.ctx = new (window.AudioContext || window.webkitAudioContext)({ sampleRate: this.sampleRate })
    if (this.ctx.state === 'suspended') await this.ctx.resume()
    this.source = this.ctx.createMediaStreamSource(this.stream)
    // ScriptProcessorNode 已被标记弃用但仍全浏览器可用；AudioWorklet 需额外模块文件，这里保持简单
    this.processor = this.ctx.createScriptProcessor(4096, 1, 1)
    this.processor.onaudioprocess = (e) => {
      if (!this.recording) return
      const input = e.inputBuffer.getChannelData(0)
      const int16 = this.floatTo16k(input, e.inputBuffer.sampleRate)
      this.accum.push(int16)
      if (this.onChunk) this.onChunk(int16)
    }
    this.source.connect(this.processor)
    this.processor.connect(this.ctx.destination)
    this.recording = true
  }

  /** Float32 → Int16，必要时重采样到 16k（浏览器可能强制用设备采样率） */
  floatTo16k(float32, fromRate) {
    let data = float32
    if (fromRate !== this.sampleRate) {
      const ratio = fromRate / this.sampleRate
      const outLen = Math.floor(float32.length / ratio)
      const out = new Float32Array(outLen)
      for (let i = 0; i < outLen; i++) {
        out[i] = float32[Math.floor(i * ratio)]
      }
      data = out
    }
    const int16 = new Int16Array(data.length)
    for (let i = 0; i < data.length; i++) {
      const s = Math.max(-1, Math.min(1, data[i]))
      int16[i] = s < 0 ? s * 0x8000 : s * 0x7fff
    }
    return int16
  }

  stop() {
    this.recording = false
    if (this.processor) {
      try { this.processor.disconnect() } catch { /* ignore */ }
    }
    if (this.source) {
      try { this.source.disconnect() } catch { /* ignore */ }
    }
    if (this.stream) this.stream.getTracks().forEach((t) => t.stop())
    if (this.ctx) {
      try { this.ctx.close() } catch { /* ignore */ }
    }
    this.processor = null
    this.source = null
    this.stream = null
    this.ctx = null
  }

  /** 把已录 PCM 合并封装为 WAV Blob（回退 HTTP 路径用） */
  getWavBlob() {
    const total = this.accum.reduce((n, c) => n + c.length, 0)
    const merged = new Int16Array(total)
    let off = 0
    for (const c of this.accum) {
      merged.set(c, off)
      off += c.length
    }
    this.accum = []
    return pcmToWavBlob(merged, this.sampleRate)
  }
}
