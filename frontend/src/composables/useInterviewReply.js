/**
 * 面试官回复编排：发消息 / SSE 流式回复 / 非流式回退 / WebSocket 一轮收口。
 * 消息数组与 sending 状态由调用方注入，本文件只负责“把一轮回答写进消息列表并触发副作用”。
 */
import { ref, computed } from 'vue'
import { respondMockInterview, respondMockInterviewStream } from '../api/mock-interview'

/**
 * @param {object} options
 * @param {import('vue').Ref<string|null>} options.sessionId 当前面试会话 id
 * @param {import('vue').Ref<Array>} options.messages 消息列表
 * @param {import('vue').Ref<string>} options.textInput 输入框文本（失败时回填）
 * @param {import('vue').ComputedRef<any>} options.currentPersonaId 当前生效的人设 id
 * @param {import('vue').Ref<boolean>} options.autoTts 是否自动朗读
 * @param {import('vue').Ref<boolean>} options.ended 面试是否已结束（宣布结束时置 true）
 * @param {import('vue').Ref<boolean>} options.sending 回复发送中（与语音链路共用）
 * @param {() => void} [options.onEnded] 面试官宣布结束
 * @param {(text: string) => void} [options.onSpeak] 即时 TTS 朗读
 * @param {(audioPath: string) => void} [options.onReplay] 回放后端已保存的语音
 * @param {(personaId: any) => void} [options.onSwitchSuggestion] 面试官建议换人
 * @param {() => void} [options.onAppend] 消息追加/增长后滚动到底部
 */
export function useInterviewReply(options = {}) {
  const {
    sessionId = null,
    messages = null,
    textInput = null,
    currentPersonaId = null,
    autoTts = null,
    ended = null,
    sending = ref(false),
    onEnded = () => {},
    onSpeak = () => {},
    onReplay = () => {},
    onSwitchSuggestion = () => {},
    onAppend = () => {},
  } = options

  // ---------- 流式回复状态 ----------
  const streamingInProgress = ref(false) // 是否有流式回复气泡正在增长
  const streamingWsBubble = ref(null) // WebSocket 流式回复中的面试官气泡
  const streamingCandidate = ref(null) // WebSocket 流式路径下乐观插入的候选人消息
  const thinking = computed(() => sending.value && !streamingInProgress.value) // 面试官思考中

  function autoTtsOn() {
    return autoTts ? !!autoTts.value : true
  }

  function personaId() {
    return currentPersonaId ? currentPersonaId.value : null
  }

  // 标记面试结束（面试官宣布）并交给上层弹窗确认
  function markEnded() {
    if (ended) ended.value = true
    onEnded()
  }

  // ---------- 流式回复（Phase A：SSE 打字机 + 完整语音） ----------
  function beginStreamingReply() {
    streamingInProgress.value = true
    messages.value.push({
      id: null,
      role: 'INTERVIEWER',
      content: '',
      personaId: personaId(),
      createdAt: new Date(),
      streaming: true,
    })
    return messages.value[messages.value.length - 1]
  }

  function finalizeSseReply(streamingBubble, data) {
    streamingInProgress.value = false
    const sidx = messages.value.indexOf(streamingBubble)
    if (sidx >= 0 && data?.aiMessage) messages.value[sidx] = data.aiMessage
    else if (sidx >= 0) messages.value.splice(sidx, 1)
    const aiContent = data?.aiMessage?.content || ''
    if (aiContent.startsWith('【面试结束】')) {
      markEnded()
    } else if (autoTtsOn()) {
      if (data?.aiMessage?.audioPath) onReplay(data.aiMessage.audioPath)
      else onSpeak(aiContent)
    }
    onSwitchSuggestion(data?.suggestedPersonaId)
  }

  async function sendStreaming(text, audioPath = null) {
    if (!sessionId.value || sending.value) return
    const optimistic = {
      id: null,
      role: 'CANDIDATE',
      content: text,
      audioPath,
      createdAt: new Date(),
    }
    messages.value.push(optimistic)
    sending.value = true
    const streamingBubble = beginStreamingReply()
    let finalized = false

    try {
      await respondMockInterviewStream(sessionId.value, { userText: text, audioPath }, ({ event, data }) => {
        if (event === 'delta') {
          streamingBubble.content += data.text || ''
          onAppend()
        } else if (event === 'done') {
          finalized = true
          const idx = messages.value.indexOf(optimistic)
          // 后端可能不回传 userMessage，必须回退到乐观对象，否则会把 undefined
          // 塞进 messages，模板访问 m.role 时直接抛错白屏
          const userMessage = data?.userMessage || optimistic
          if (idx >= 0) messages.value[idx] = userMessage
          else messages.value.push(userMessage)
          finalizeSseReply(streamingBubble, data)
        } else if (event === 'error') {
          throw new Error(data?.message || '流式回复失败')
        }
      })
      if (!finalized) throw new Error('流式回复中断')
    } catch (e) {
      if (finalized) {
        ElMessage.error(`回复处理出错：${e.message || '未知错误'}`)
      } else {
        // 回退到非流式
        messages.value = messages.value.filter((m) => m !== optimistic && m !== streamingBubble)
        textInput.value = text
        ElMessage.warning('流式回复不可用，已切换到普通模式')
        await doSend(text, audioPath)
      }
    } finally {
      sending.value = false
    }
  }

  // ---------- 非流式回退 ----------
  async function doSend(text, audioPath = null) {
    if (!sessionId.value) return
    // 乐观插入候选人消息，即时反馈
    const optimistic = {
      id: null,
      role: 'CANDIDATE',
      content: text,
      audioPath,
      createdAt: new Date(),
    }
    messages.value.push(optimistic)
    sending.value = true
    try {
      const res = await respondMockInterview(sessionId.value, { userText: text, audioPath })
      const data = res?.data
      if (!data?.aiMessage) {
        ElMessage.error(res?.message || '面试官回复失败')
        messages.value = messages.value.filter((m) => m !== optimistic)
        return
      }
      // 用服务端保存的消息替换乐观消息，并追加 AI 回复
      const idx = messages.value.indexOf(optimistic)
      // 同流式分支：userMessage 缺失时回退乐观对象，避免 undefined 进入列表
      const savedUserMessage = data.userMessage || optimistic
      if (idx >= 0) messages.value[idx] = savedUserMessage
      else messages.value.push(savedUserMessage)
      // aiMessage 也可能缺失：先归一化为占位对象，后续读取不再需要判空
      const aiMessage = data.aiMessage || { role: 'INTERVIEWER', content: '' }
      messages.value.push(aiMessage)

      const aiContent = aiMessage.content || ''
      if (aiContent.startsWith('【面试结束】')) {
        markEnded()
      } else if (autoTtsOn()) {
        // 优先播放后端已合成保存的面试官语音；无音频时即时 TTS 合成
        if (aiMessage.audioPath) {
          onReplay(aiMessage.audioPath)
        } else {
          onSpeak(aiContent)
        }
      }

      // 面试官建议换人：根据回答情况推荐更合适的风格，弹窗由候选人确认
      onSwitchSuggestion(data.suggestedPersonaId)
    } catch {
      // 失败：把文本放回输入框，方便重发
      messages.value = messages.value.filter((m) => m !== optimistic)
      textInput.value = text
    } finally {
      sending.value = false
    }
  }

  // ---------- WebSocket 流式链路回调 ----------
  // 识别文本就绪即显示候选人消息（不等待 LLM 回复），并清除中间识别提示
  function onVoiceUserText(data) {
    messages.value.push({
      id: null,
      role: 'CANDIDATE',
      content: data?.text || '',
      audioPath: data?.audioPath || null,
      createdAt: new Date(),
    })
    streamingCandidate.value = messages.value[messages.value.length - 1]
    onAppend()
  }

  function onVoiceDelta(delta) {
    if (!streamingWsBubble.value) streamingWsBubble.value = beginStreamingReply()
    streamingWsBubble.value.content += delta || ''
    onAppend()
  }

  // 每轮录音开始前复位流式气泡引用
  function resetVoiceBubbles() {
    streamingWsBubble.value = null
    streamingCandidate.value = null
  }

  // WebSocket 流式一轮结束：收口候选人/面试官消息
  function finalizeVoiceReply(data) {
    streamingInProgress.value = false
    const bubble = streamingWsBubble.value
    streamingWsBubble.value = null
    const aiMessage = data?.aiMessage || {
      id: null,
      role: 'INTERVIEWER',
      content: bubble?.content || '',
      personaId: personaId(),
      createdAt: new Date(),
    }

    // 候选人消息：替换 onUserText 时乐观插入的那条
    const cand = streamingCandidate.value
    streamingCandidate.value = null
    if (cand && data?.userMessage) {
      const cidx = messages.value.indexOf(cand)
      if (cidx >= 0) messages.value[cidx] = data.userMessage
    } else if (data?.userMessage) {
      messages.value.push(data.userMessage)
    }

    // 面试官消息：替换流式气泡或追加
    if (bubble) {
      const sidx = messages.value.indexOf(bubble)
      if (sidx >= 0) messages.value.splice(sidx, 1, aiMessage)
      else messages.value.push(aiMessage)
    } else {
      messages.value.push(aiMessage)
    }

    const aiContent = aiMessage.content || ''
    if (aiContent.startsWith('【面试结束】')) {
      markEnded()
    }
    onSwitchSuggestion(data?.suggestedPersonaId)
  }

  return {
    sending,
    thinking,
    streamingInProgress,
    sendStreaming,
    doSend,
    finalizeVoiceReply,
    onVoiceUserText,
    onVoiceDelta,
    resetVoiceBubbles,
  }
}
