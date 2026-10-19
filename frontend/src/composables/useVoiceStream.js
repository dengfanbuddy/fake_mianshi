/**
 * 面试语音流式链路（Phase B：WebSocket 全链路），只负责“语音与音频资源”，
 * 消息数组 / 气泡 / 发送状态仍由父组件编排：
 * - /api/ws/interview 连接与每轮重连（createVoiceStream）
 * - 录音期间 PCM 分块发送（pcmRecorder 事件 → push / 就绪前缓存）
 * - 面试官语音 PCM 播放队列（PcmQueuePlayer）与 stopCurrentAudio
 * - 麦克风录音状态、HTTP 回退（onRecorded → blobToWav → STT → 上传 → 交给上层发送）
 * - 页面销毁时的资源释放（WS / AudioContext / Audio 元素）
 */
import { ref } from 'vue'
import { recognizeSpeech, synthesizeSpeech, uploadAudio } from '../api/voice'
import { blobToWav } from '../utils/audio'
import { createVoiceStream } from '../utils/voiceStream'
import { PcmQueuePlayer } from '../utils/audioQueue'

/**
 * @param {object} options
 * @param {import('vue').Ref<string|null>} options.sessionId 当前面试会话 id
 * @param {import('vue').Ref<boolean>} [options.autoTts] 是否自动播放面试官语音（默认 true）
 * @param {import('vue').Ref<object|null>} [options.voiceStyle] TTS 音色（面试官 styleConfig）
 * @param {() => boolean} [options.isSending] 读取父组件的 sending 状态（防并发）
 * @param {(v: boolean) => void} [options.setSending] 写父组件的 sending 状态
 * @param {() => boolean} [options.isEnded] 面试是否已结束
 * @param {(data: { text?: string, audioPath?: string|null }) => void} [options.onUserText] 候选人识别文本就绪
 * @param {(delta: string) => void} [options.onAssistantText] 面试官回复增量
 * @param {(data: object) => void} [options.onDone] 一轮流式结束（携带 userMessage / aiMessage 等）
 * @param {(msg: string) => void} [options.onError] 流式链路出错
 * @param {() => void} [options.onSttEmpty] 未识别到内容
 * @param {(text: string, audioPath: string|null) => void} [options.submitText] HTTP 回退识别成功后交给上层发送
 */
export function useVoiceStream(options = {}) {
  const {
    sessionId = null,
    autoTts = null,
    voiceStyle = null,
    isSending = () => false,
    setSending = () => {},
    isEnded = () => false,
    onUserText = null,
    onAssistantText = null,
    onDone = null,
    onError = null,
    onSttEmpty = null,
    submitText = null,
  } = options

  // ---------- 语音状态 ----------
  const wsReady = ref(false) // WebSocket 流式识别已就绪
  const partialText = ref('') // 流式 ASR 中间识别文本
  const recording = ref(false) // 是否正在按住录音（用于禁用文字输入，避免并行提交）
  const recognizing = ref(false) // STT 识别中

  const wsBufferedChunks = [] // WS 就绪前缓存的 PCM 分片
  let voiceStream = null // WebSocket 流式控制器
  let pcmPlayer = null // 面试官语音 PCM 播放队列
  let currentAudio = null // 正在播放的面试官语音
  let disposed = false // 清理幂等标记：避免重复关闭同一资源

  function wantTts() {
    return autoTts ? !!autoTts.value : true
  }

  function ensurePcmPlayer() {
    if (!pcmPlayer) pcmPlayer = new PcmQueuePlayer()
    return pcmPlayer
  }

  // ---------- 录音 ----------
  function onRecStart() {
    recording.value = true
    wsReady.value = false
    partialText.value = ''
    wsBufferedChunks.length = 0
    const player = ensurePcmPlayer()
    player.stop()
    player.prime() // 在用户手势中解锁 AudioContext，避免自动播放被拦截
    // 上一轮流式连接若未关闭，先关闭（每轮使用新连接）
    if (voiceStream) {
      voiceStream.close()
      voiceStream = null
    }
    if (!sessionId?.value || isEnded()) return

    const stream = createVoiceStream({
      sessionId: sessionId.value,
      onReady: () => {
        wsReady.value = true
        for (const c of wsBufferedChunks) stream.push(c)
        wsBufferedChunks.length = 0
      },
      onPartial: (txt) => {
        partialText.value = txt
      },
      onUserText: (data) => {
        // 识别文本就绪即显示候选人消息（不等待 LLM 回复），并清除中间识别提示
        partialText.value = ''
        if (onUserText) onUserText(data)
      },
      onText: (delta) => {
        if (onAssistantText) onAssistantText(delta || '')
      },
      onAudio: (int16) => {
        if (wantTts()) ensurePcmPlayer().enqueue(int16)
      },
      onDone: (data) => {
        setSending(false)
        if (onDone) onDone(data)
      },
      onError: (msg) => {
        setSending(false)
        partialText.value = ''
        if (onError) onError(msg || '流式语音出错')
        else ElMessage.error(msg || '流式语音出错')
      },
      onSttEmpty: () => {
        setSending(false)
        partialText.value = ''
        if (onSttEmpty) onSttEmpty()
        else ElMessage.warning('未能识别到内容，请重新说话或改用文字输入')
      },
    })
    voiceStream = stream
    stream.connect().catch(() => {
      // WS 建立失败：保持 wsReady=false，回退 HTTP 路径
      voiceStream = null
    })
  }

  function onRecChunk(int16) {
    if (wsReady.value && voiceStream) {
      voiceStream.push(int16)
    } else {
      wsBufferedChunks.push(int16)
    }
  }

  async function onRecRecorded(blob) {
    // 先复位录音状态：录音异常/过短时 blob 为 null，也必须解锁输入框
    recording.value = false
    if (wsReady.value && voiceStream) {
      // 流式模式：松开发送，后续由 WS 事件驱动
      setSending(true)
      voiceStream.end()
      return
    }
    // 未就绪：回退 HTTP 路径（STT + 流式回复）
    const stream = voiceStream
    voiceStream = null
    if (stream) stream.close()
    // 无有效录音（AudioRecorder 已给出提示）：仅复位状态，不再走 STT
    if (!blob) return
    await handleRecorded(blob)
  }

  // 语音录音结束 → STT → 保存录音 → respond
  async function handleRecorded(blob) {
    if (isSending() || recognizing.value || isEnded()) return
    recognizing.value = true
    try {
      // 浏览器录制多为 webm，转 wav 后上传（腾讯云 ASR 对 wav 支持最稳）
      const wavBlob = await blobToWav(blob)
      const res = await recognizeSpeech(wavBlob)
      recognizing.value = false
      const text = (res?.data || '').trim()
      if (!text) {
        ElMessage.warning('未能识别到内容，请重新说话或改用文字输入')
        return
      }
      // 保存录音供后续回听（失败不阻断对话，仅提示）
      let audioPath = null
      try {
        const up = await uploadAudio(sessionId?.value, wavBlob)
        audioPath = up?.data || null
      } catch (e) {
        ElMessage.warning('录音保存失败，本次回答将无语音回放')
      }
      if (submitText) submitText(text, audioPath)
    } catch (e) {
      recognizing.value = false
      ElMessage.error(`语音识别失败：${e.message || '未知错误'}，请改用文字输入`)
    }
  }

  // ---------- 语音播放 ----------
  function stopCurrentAudio() {
    if (currentAudio) {
      currentAudio.pause()
      currentAudio.src = ''
      currentAudio = null
    }
  }

  async function speak(text) {
    stopCurrentAudio()
    const cleanText = ttsText(text)
    if (!cleanText) return
    let blob
    try {
      blob = await synthesizeSpeech(cleanText, voiceStyle ? voiceStyle.value : null)
    } catch (e) {
      // 合成请求失败（网络 / 后端错误）
      const msg = e?.response?.data?.message || e?.message || '未知错误'
      ElMessage.error(`语音合成失败：${msg}`)
      return
    }
    if (!blob || blob.size === 0) {
      ElMessage.error('语音合成失败：音频为空')
      return
    }
    const url = URL.createObjectURL(blob)
    const audio = new Audio(url)
    currentAudio = audio
    audio.onended = () => {
      URL.revokeObjectURL(url)
      if (currentAudio === audio) currentAudio = null
    }
    audio.onerror = () => {
      URL.revokeObjectURL(url)
      if (currentAudio === audio) currentAudio = null
      ElMessage.error('语音播放失败：音频格式可能不受支持')
    }
    try {
      await audio.play()
    } catch (e) {
      // 浏览器自动播放策略拦截：用户点击页面后可恢复播放
      URL.revokeObjectURL(url)
      if (currentAudio === audio) currentAudio = null
      const isAutoplayBlocked = e?.name === 'NotAllowedError' || /autoplay|interactive/i.test(e?.message || '')
      if (isAutoplayBlocked) {
        ElMessage.warning('浏览器拦截了自动朗读，请点击一下页面任意位置后重试')
      } else {
        ElMessage.error(`语音播放失败：${e?.message || '未知错误'}`)
      }
    }
  }

  function playReplay(audioPath) {
    if (!audioPath) return
    stopCurrentAudio()
    const audio = new Audio(`/api/voice/audio/${audioPath}`)
    currentAudio = audio
    audio.onended = () => {
      if (currentAudio === audio) currentAudio = null
    }
    audio.onerror = () => ElMessage.error('音频回放失败')
    audio.play().catch(() => ElMessage.error('音频回放失败'))
  }

  // TTS 朗读前去掉结束标记
  function ttsText(content = '') { return content.replace(/^【面试结束】/, '').trim() }

  /**
   * 释放音频/连接资源（幂等，可重复调用）。
   * 缺了这段会导致：面试官语音继续播放、WebSocket 不断开、
   * AudioContext 不关闭（浏览器有数量上限，多进几次就会静默失声）。
   */
  function dispose() {
    if (disposed) return
    disposed = true
    // 1. 关闭流式语音 WebSocket
    if (voiceStream) {
      try {
        voiceStream.close()
      } catch (e) {
        // 忽略：连接可能已断开
      }
      voiceStream = null
    }
    // 2. 停止并释放 PCM 播放队列（内部会 close AudioContext）
    if (pcmPlayer) {
      try {
        pcmPlayer.close()
      } catch (e) {
        // 忽略
      }
      pcmPlayer = null
    }
    // 3. 停止正在播放的面试官语音
    stopCurrentAudio()
    wsBufferedChunks.length = 0
  }

  return {
    wsReady, partialText, recording, recognizing, // 状态
    onRecStart, onRecChunk, onRecRecorded, // AudioRecorder 事件
    speak, playReplay, stopCurrentAudio, // 播放
    dispose, // 生命周期
  }
}
