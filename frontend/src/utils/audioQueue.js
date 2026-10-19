/**
 * PCM 流式播放队列：把后端推送的 Int16 PCM 分片（16k 单声道）按时间线连续播放，无断点。
 *
 * **采样率必须与数据一致（重要）**
 * 早期版本用 `new AudioContext()`（即设备默认 48k/44.1k）承载 16k 的 AudioBuffer，
 * 结果浏览器要对**每个缓冲分别**做采样率转换，接缝处出现满幅阶跃：
 * 实测正弦（幅值 0.5）在接缝处跳到 0.92，23 个接缝里 16 个是坏的，听感就是持续刺耳咔哒。
 * 改成 `new AudioContext({ sampleRate: 16000 })` 后整条音频图里没有任何采样率转换
 * （只有输出端一次性把 16k 送到设备），实测 23 个接缝 0 个异常。
 */
export class PcmQueuePlayer {
  constructor(sampleRate = 16000) {
    this.sampleRate = sampleRate
    this.ctx = null
    this.nextTime = 0
    this.sources = new Set()
    this.closed = false
    /** 浏览器不支持指定采样率时才用：跨缓冲相位连续的线性重采样状态 */
    this._rs = null
    this._needFadeIn = true
  }

  _ensureCtx() {
    if (!this.ctx) {
      const Ctor = window.AudioContext || window.webkitAudioContext
      let ctx = null
      try {
        // 首选：让上下文采样率等于 PCM 采样率，避免逐块转换
        ctx = new Ctor({ sampleRate: this.sampleRate })
      } catch (e) {
        ctx = new Ctor()
      }
      this.ctx = ctx
      if (ctx.sampleRate !== this.sampleRate) {
        // 少数浏览器（旧 Firefox/Safari）会忽略 sampleRate。
        // 退化为手写重采样：状态（相位 + 上一块最后一个样本）跨缓冲保留，
        // 保证接缝连续——每块各转一次正是刺耳噪音的成因。
        console.warn(
          `[audio] AudioContext 采样率 ${ctx.sampleRate}Hz 无法对齐 ${this.sampleRate}Hz，` +
            '已启用内置重采样（音质略降但没有接缝爆音）'
        )
        this._rs = { phase: 0, prev: 0 }
      }
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

  /** 跨缓冲相位连续的线性重采样（仅回退路径使用） */
  _resample(f32) {
    const { phase, prev } = this._rs
    const inLen = f32.length
    const ctxRate = this.ctx.sampleRate
    const step = this.sampleRate / ctxRate
    const outLen = Math.max(1, Math.round((inLen * ctxRate) / this.sampleRate))
    const out = new Float32Array(outLen)
    const at = (i) => {
      if (i < 0) return prev
      if (i >= inLen) return f32[inLen - 1]
      return f32[i]
    }
    let x = phase
    let xEnd = x
    for (let k = 0; k < outLen; k++) {
      x += step
      const i0 = Math.floor(x)
      const t = x - i0
      const a = at(i0 - 1)
      const b = at(i0)
      out[k] = a + (b - a) * t
      xEnd = x
    }
    this._rs.phase = Math.max(0, xEnd - inLen)
    this._rs.prev = f32[inLen - 1]
    return out
  }

  /** 入队一段 Int16 PCM，按时间线连续播放 */
  enqueue(int16Pcm) {
    if (this.closed) return
    if (!int16Pcm || int16Pcm.length === 0) return
    const ctx = this._ensureCtx()
    const int16 = int16Pcm instanceof Int16Array ? int16Pcm : new Int16Array(int16Pcm)
    let f32 = new Float32Array(int16.length)
    for (let i = 0; i < int16.length; i++) {
      f32[i] = int16[i] / 32768
    }
    if (this._rs) {
      f32 = this._resample(f32)
    }
    // 每轮第一块做 4ms 淡入：从静音直接切进波形会有起播爆音
    if (this._needFadeIn) {
      const fade = Math.min(f32.length, Math.round(this.sampleRate * 0.004))
      for (let i = 0; i < fade; i++) {
        f32[i] *= i / fade
      }
      this._needFadeIn = false
    }
    const buf = ctx.createBuffer(1, f32.length, ctx.sampleRate)
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
    // 必须复位时间线：否则下一轮的首包会被排到上一轮遗留的 nextTime（可能几秒后），
    // 表现为「面试官这段没声音」。
    this.nextTime = 0
    this._needFadeIn = true
    if (this._rs) {
      this._rs.phase = 0
      this._rs.prev = 0
    }
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
