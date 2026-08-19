/**
 * PCM 流式播放队列：把后端推送的 Int16 PCM 分片（16k 单声道）按时间线连续播放，无断点。
 */
export class PcmQueuePlayer {
  constructor(sampleRate = 16000) {
    this.sampleRate = sampleRate
    this.ctx = null
    this.nextTime = 0
    this.sources = new Set()
    this.closed = false
  }

  _ensureCtx() {
    if (!this.ctx) {
      this.ctx = new (window.AudioContext || window.webkitAudioContext)()
    }
    if (this.ctx.state === 'suspended') {
      this.ctx.resume()
    }
    return this.ctx
  }

  /** 在用户手势中预创建并解锁 AudioContext，避免自动播放策略拦截 */
  prime() {
    const ctx = this._ensureCtx()
    if (ctx.state === 'suspended') {
      ctx.resume()
    }
    return ctx
  }

  /** 入队一段 Int16 PCM（16k 单声道），按时间线连续播放 */
  enqueue(int16Pcm) {
    if (this.closed) return
    if (!int16Pcm || int16Pcm.length === 0) return
    const ctx = this._ensureCtx()
    const int16 = int16Pcm instanceof Int16Array ? int16Pcm : new Int16Array(int16Pcm)
    const f32 = new Float32Array(int16.length)
    for (let i = 0; i < int16.length; i++) {
      f32[i] = int16[i] / 32768
    }
    const buf = ctx.createBuffer(1, f32.length, this.sampleRate)
    buf.copyToChannel(f32, 0)

    const when = Math.max(ctx.currentTime + 0.02, this.nextTime)
    const src = ctx.createBufferSource()
    src.buffer = buf
    src.connect(ctx.destination)
    src.start(when)
    this.nextTime = when + buf.duration
    this.sources.add(src)
    src.onended = () => this.sources.delete(src)
  }

  stop() {
    for (const s of this.sources) {
      try { s.stop() } catch { /* ignore */ }
    }
    this.sources.clear()
  }

  close() {
    this.closed = true
    this.stop()
    if (this.ctx) {
      try { this.ctx.close() } catch { /* ignore */ }
      this.ctx = null
    }
  }
}
