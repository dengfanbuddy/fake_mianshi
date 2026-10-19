import { pcmToWavBlob } from './audio'

/** 探测「麦克风到底有没有在送数据」的时长（毫秒） */
const PROBE_MS = 500
/** 判定为「有信号」的阈值：真实麦克风即使安静，噪声底也远大于它；驱动/静音键导致的静音是精确 0 */
const SIGNAL_EPS = 1e-4

/**
 * 16k 单声道 PCM 录音器（基于 AudioContext + ScriptProcessorNode）。
 * 相比 MediaRecorder，直接产出 16k Int16 PCM，便于流式推给实时 ASR，也便于回退封装 WAV 走 HTTP。
 *
 * 打开麦克风时会做「静音探测」，因为踩过这个坑：用户按住说话、录音正常结束，但存下来的 wav 是
 * **精确全零**（peak=0.0），后端 ASR 只能返回空文本，界面显示「未能识别到内容」——把
 * 「麦克风没数据」误报成「识别不出内容」，用户完全无从下手。真实存在两类诱因：
 *   1. 系统静音 / 选错输入设备 / Windows 隐私设置禁用麦克风；
 *   2. 部分 Windows 驱动在 `new AudioContext({sampleRate:16000})` 下会给出全零。
 * 所以这里先按标准方式开，探测 500ms 无信号就自动换成「原始流 + 设备默认采样率」重开一次；
 * 两种都静音则通过 {@link PcmRecorder} 的 `onSilent` 明确告知用户，而不是让录音"看起来成功"。
 */
export class PcmRecorder {
  constructor({ sampleRate = 16000, onChunk, onError, onSilent, onLevel } = {}) {
    this.sampleRate = sampleRate
    this.onChunk = onChunk
    this.onError = onError
    this.onSilent = onSilent
    this.onLevel = onLevel
    this.ctx = null
    this.source = null
    this.processor = null
    this.stream = null
    this.recording = false
    this.accum = [] // 累积的 Int16 分片，供回退封装 WAV
    this.peak = 0 // 本次录音见过的最大幅度（诊断用）
    this.emitting = false // 探测阶段不把音频塞进 accum / onChunk
    this.pending = [] // 探测阶段的音频先存着，resume() 时补发，保证一个字节都不丢
    this.compatMode = false // 是否降级成了「原始流 + 默认采样率」
    this._lastLevelAt = 0
  }

  async start() {
    const standard = {
      audio: {
        channelCount: 1,
        echoCancellation: true,
        noiseSuppression: true,
        autoGainControl: true,
      },
      sampleRate: this.sampleRate,
    }

    try {
      await this.open(standard)
    } catch (e) {
      // 权限被拒 / 没有设备：重试没有意义，直接交给上层提示
      if (this.onError) this.onError(e)
      throw e
    }

    if (await this.hasSignal(PROBE_MS)) {
      return
    }

    // 500ms 全是精确零 → 换兼容方式（设备默认采样率、不加任何音频处理）重开一次
    this.teardown()
    try {
      await this.open({ audio: { channelCount: 1 }, sampleRate: null })
      this.compatMode = true
      if (await this.hasSignal(PROBE_MS)) {
        if (this.onSilent) this.onSilent('switched')
      } else if (this.onSilent) {
        this.onSilent('silent')
      }
    } catch (e) {
      // 兼容方式也开不起来：退回标准方式继续录，不阻塞用户
      this.teardown()
      await this.open(standard)
      if (this.onSilent) this.onSilent('silent')
    }
  }

  /** 打开一路麦克风并接上 PCM 处理器（不负责静音探测） */
  async open({ audio, sampleRate }) {
    this.stream = await navigator.mediaDevices.getUserMedia({ audio })
    const Ctor = window.AudioContext || window.webkitAudioContext
    this.ctx = sampleRate ? new Ctor({ sampleRate }) : new Ctor()
    if (this.ctx.state === 'suspended') {
      try { await this.ctx.resume() } catch { /* 部分浏览器需用户手势，忽略 */ }
    }
    this.peak = 0
    this.emitting = false
    this.pending = []
    this.source = this.ctx.createMediaStreamSource(this.stream)
    // ScriptProcessorNode 已被标记弃用但仍全浏览器可用；AudioWorklet 需额外模块文件，这里保持简单
    this.processor = this.ctx.createScriptProcessor(4096, 1, 1)
    this.processor.onaudioprocess = (e) => {
      if (!this.recording) return
      const input = e.inputBuffer.getChannelData(0)
      // 先量幅度：探测阶段要靠它判断麦克风有没有在送数据
      let localPeak = 0
      for (let i = 0; i < input.length; i += 8) {
        const v = input[i] < 0 ? -input[i] : input[i]
        if (v > localPeak) localPeak = v
      }
      if (localPeak > this.peak) this.peak = localPeak
      const now = Date.now()
      if (this.onLevel && now - this._lastLevelAt > 60) {
        this._lastLevelAt = now
        this.onLevel(Math.min(1, localPeak * 6))
      }
      const int16 = this.floatTo16k(input, e.inputBuffer.sampleRate)
      if (!this.emitting) {
        // 探测阶段先攒着：resume() 时补发，避免丢掉用户最开头那半秒
        this.pending.push(int16)
        return
      }
      this.accum.push(int16)
      if (this.onChunk) this.onChunk(int16)
    }
    this.source.connect(this.processor)
    this.processor.connect(this.ctx.destination)
    this.recording = true
  }

  /**
   * 探测通过后开始正式输出：把探测阶段攒下的音频先补发，再进入实时输出。
   *
   * 必须由调用方在「录音已开始」事件发出之后再调，否则补发的音频会被父组件
   * 丢弃（它那时候还没建立流式连接）。
   */
  resume() {
    this.emitting = true
    const buffered = this.pending
    this.pending = []
    for (const chunk of buffered) {
      this.accum.push(chunk)
      if (this.onChunk) this.onChunk(chunk)
    }
  }

  /** 在 ms 毫秒内只要出现过非零样本就返回 true */
  hasSignal(ms) {
    return new Promise((resolve) => {
      const startedAt = Date.now()
      const tick = () => {
        if (this.peak > SIGNAL_EPS) {
          resolve(true)
          return
        }
        if (Date.now() - startedAt >= ms || !this.recording) {
          resolve(false)
          return
        }
        setTimeout(tick, 50)
      }
      tick()
    })
  }

  /** Float32 → Int16，必要时重采样到目标采样率（浏览器可能强制用设备采样率） */
  floatTo16k(float32, fromRate) {
    let data = float32
    if (fromRate !== this.sampleRate) {
      const ratio = fromRate / this.sampleRate
      const outLen = Math.floor(float32.length / ratio)
      const out = new Float32Array(outLen)
      // 取区间均值（粗糙低通）而不是直接抽点：直接抽点会把 8k 以上的能量折叠进语音带内
      for (let i = 0; i < outLen; i++) {
        const start = Math.floor(i * ratio)
        const end = Math.min(float32.length, Math.max(start + 1, Math.floor((i + 1) * ratio)))
        let sum = 0
        for (let j = start; j < end; j++) sum += float32[j]
        out[i] = sum / (end - start)
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

  /** 释放当前这一路（探测失败后重开时用） */
  teardown() {
    this.recording = false
    this.pending = []
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

  stop() {
    this.teardown()
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
