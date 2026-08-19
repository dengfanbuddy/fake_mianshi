/**
 * 音频工具：把浏览器录制的 webm/ogg 等格式 blob 转换为 WAV（PCM 16bit 单声道 16kHz）
 * 腾讯云 ASR 一句话识别对 wav 支持最好，统一转 wav 上传。
 */

/**
 * 解码任意音频 blob → 重采样为 16kHz 单声道 → 编码为 WAV blob
 * @param {Blob} audioBlob 浏览器录制得到的音频
 * @param {number} sampleRate 目标采样率（腾讯云 ASR 建议 16k）
 * @returns {Promise<Blob>} wav blob
 */
export async function blobToWav(audioBlob, sampleRate = 16000) {
  // 已是 wav 直接返回
  if (audioBlob.type.includes('wav')) return audioBlob

  const AudioContextCtor = window.AudioContext || window.webkitAudioContext
  if (!AudioContextCtor) {
    throw new Error('当前浏览器不支持音频解码，无法转换录音格式')
  }

  const arrayBuffer = await audioBlob.arrayBuffer()
  const ctx = new AudioContextCtor()
  try {
    const audioBuffer = await ctx.decodeAudioData(arrayBuffer)

    // 重采样到目标采样率（单声道）
    const targetRate = sampleRate
    const channels = 1
    const ratio = audioBuffer.sampleRate / targetRate
    const outLength = Math.ceil(audioBuffer.length / ratio)

    const offlineCtx = new (window.OfflineAudioContext || window.webkitOfflineAudioContext)(
      channels,
      outLength,
      targetRate
    )
    const source = offlineCtx.createBufferSource()
    source.buffer = audioBuffer
    source.connect(offlineCtx.destination)
    source.start(0)
    const rendered = await offlineCtx.startRendering()

    // 编码 WAV（PCM 16bit）
    const numSamples = rendered.length
    const buffer = new ArrayBuffer(44 + numSamples * 2)
    const view = new DataView(buffer)

    // RIFF header
    writeString(view, 0, 'RIFF')
    view.setUint32(4, 36 + numSamples * 2, true)
    writeString(view, 8, 'WAVE')
    writeString(view, 12, 'fmt ')
    view.setUint32(16, 16, true)
    view.setUint16(20, 1, true) // PCM
    view.setUint16(22, 1, true) // mono
    view.setUint32(24, targetRate, true)
    view.setUint32(28, targetRate * 2, true) // byte rate
    view.setUint16(32, 2, true) // block align
    view.setUint16(34, 16, true) // bits per sample
    writeString(view, 36, 'data')
    view.setUint32(40, numSamples * 2, true)

    // PCM data
    const data = rendered.getChannelData(0)
    let offset = 44
    for (let i = 0; i < numSamples; i++) {
      const s = Math.max(-1, Math.min(1, data[i]))
      view.setInt16(offset, s < 0 ? s * 0x8000 : s * 0x7fff, true)
      offset += 2
    }

    return new Blob([buffer], { type: 'audio/wav' })
  } finally {
    ctx.close().catch(() => {})
  }
}

function writeString(view, offset, str) {
  for (let i = 0; i < str.length; i++) {
    view.setUint8(offset + i, str.charCodeAt(i))
  }
}

/**
 * 文本分句：按句末标点切分，单句超过 maxLen 硬切（供前端逐句 TTS 播报）。
 * @param {string} text
 * @param {number} maxLen
 * @returns {string[]}
 */
export function splitSentences(text, maxLen = 150) {
  const result = []
  if (!text) return result
  let buf = ''
  for (const ch of text) {
    buf += ch
    const isEnd = '。！？；!?;\n'.includes(ch)
    if (isEnd || buf.length >= maxLen) {
      const seg = buf.trim()
      if (seg) result.push(seg)
      buf = ''
    }
  }
  const last = buf.trim()
  if (last) result.push(last)
  return result
}

/**
 * 把 Int16 PCM（16k 单声道）封装为 WAV Blob。
 * @param {Int16Array|number[]} samples
 * @param {number} sampleRate
 * @returns {Blob}
 */
export function pcmToWavBlob(samples, sampleRate = 16000) {
  const int16 = samples instanceof Int16Array ? samples : new Int16Array(samples)
  const buffer = new ArrayBuffer(44 + int16.length * 2)
  const view = new DataView(buffer)
  writeString(view, 0, 'RIFF')
  view.setUint32(4, 36 + int16.length * 2, true)
  writeString(view, 8, 'WAVE')
  writeString(view, 12, 'fmt ')
  view.setUint32(16, 16, true)
  view.setUint16(20, 1, true) // PCM
  view.setUint16(22, 1, true) // mono
  view.setUint32(24, sampleRate, true)
  view.setUint32(28, sampleRate * 2, true) // byte rate
  view.setUint16(32, 2, true) // block align
  view.setUint16(34, 16, true) // bits per sample
  writeString(view, 36, 'data')
  view.setUint32(40, int16.length * 2, true)
  for (let i = 0; i < int16.length; i++) {
    view.setInt16(44 + i * 2, int16[i], true)
  }
  return new Blob([buffer], { type: 'audio/wav' })
}
