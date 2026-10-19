<script setup>
/**
 * AI 模拟面试对话页：只做会话编排与组合。
 * - 会话（开始 / 结束 / 刷新恢复）：composables/useInterviewSession
 * - 回复（发消息 / 流式回复 / 非流式回退）：composables/useInterviewReply
 * - 语音（WebSocket 流式 + PCM 播放 + 录音回退）：composables/useVoiceStream
 * - 换面试官（手动 + AI 建议）：composables/usePersonaSwitch
 * - 展示区块：components/mock-interview/*
 */
import { ref, onBeforeUnmount, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useInterviewSession } from '../../composables/useInterviewSession'
import { useInterviewReply } from '../../composables/useInterviewReply'
import { useVoiceStream } from '../../composables/useVoiceStream'
import { usePersonaSwitch } from '../../composables/usePersonaSwitch'
import MockInterviewConfigCard from '../../components/mock-interview/MockInterviewConfigCard.vue'
import MockInterviewHeader from '../../components/mock-interview/MockInterviewHeader.vue'
import MockInterviewMessageList from '../../components/mock-interview/MockInterviewMessageList.vue'
import MockInterviewComposer from '../../components/mock-interview/MockInterviewComposer.vue'
import MockInterviewPersonaDialog from '../../components/mock-interview/MockInterviewPersonaDialog.vue'

const router = useRouter()

// ---------- 会话编排 ----------
const {
  sessionId,
  messages,
  outline,
  personaName,
  ended,
  loading,
  starting,
  ending,
  personas,
  selectedPersonaId,
  referenceWrittenTest,
  currentPersonaId,
  currentPersonaName,
  currentPersonaStyle,
  handleStart,
  endInterview,
  handleInterviewEnded,
} = useInterviewSession()

// ---------- 交互状态 ----------
const sending = ref(false) // 回复发送中（回复链路与语音链路共用）
const textInput = ref('')
const autoTts = ref(true)
const messageListRef = ref(null) // 消息列表子组件（暴露 scrollToBottom）

// ---------- 自动滚动 ----------
function scrollToBottom() {
  messageListRef.value?.scrollToBottom()
}

// ---------- 换面试官编排 ----------
const {
  switching,
  switchDialogVisible,
  switchPersonaId,
  setSwitchPersonaId,
  openSwitchDialog,
  confirmSwitch,
  handleSwitchSuggestion,
} = usePersonaSwitch({
  sessionId,
  personas,
  personaName,
  messages,
  currentPersonaId,
  isEnded: () => ended.value,
})

// ---------- 回复编排（发消息 / 流式回复） ----------
const reply = useInterviewReply({
  sessionId,
  messages,
  textInput,
  currentPersonaId,
  autoTts,
  ended,
  sending,
  onEnded: handleInterviewEnded,
  // 语音实例在下方创建，这里用函数声明保证调用时已就绪
  onSpeak: (text) => speakReply(text),
  onReplay: (audioPath) => replayReply(audioPath),
  onSwitchSuggestion: (id) => handleSwitchSuggestion(id),
  onAppend: scrollToBottom,
})

// 面试官思考中（sending 且没有流式气泡在增长）
const thinking = reply.thinking

// ---------- 语音编排（WebSocket 流式 + PCM 播放 + HTTP 回退） ----------
const {
  partialText,
  recording,
  recognizing,
  onRecStart,
  onRecChunk,
  onRecRecorded,
  speak,
  playReplay,
  dispose: disposeVoice,
} = useVoiceStream({
  sessionId,
  autoTts,
  voiceStyle: currentPersonaStyle,
  isSending: () => sending.value,
  setSending: (v) => {
    sending.value = v
  },
  isEnded: () => ended.value,
  onUserText: (data) => reply.onVoiceUserText(data),
  onAssistantText: (delta) => reply.onVoiceDelta(delta),
  onDone: (data) => reply.finalizeVoiceReply(data),
  onError: (msg) => ElMessage.error(msg || '流式语音出错'),
  onSttEmpty: () => ElMessage.warning('未能识别到内容，请重新说话或改用文字输入'),
  // WS 未就绪时的 HTTP 回退：识别成功 → 交回回复链路发送
  submitText: (text, audioPath) => reply.sendStreaming(text, audioPath),
})

function speakReply(text) {
  speak(text)
}

function replayReply(audioPath) {
  playReplay(audioPath)
}

// ---------- 发消息 ----------
function sendText() {
  const text = textInput.value.trim()
  if (!text) {
    ElMessage.warning('请先输入内容')
    return
  }
  if (sending.value || recognizing.value) return
  textInput.value = ''
  reply.sendStreaming(text)
}

// 录音开始：先复位上一轮流式气泡引用，再交给语音链路建连
function handleRecStart() {
  reply.resetVoiceBubbles()
  onRecStart()
}

// ---------- 滚动监听 ----------
watch(
  () => [messages.value.length, thinking.value, recognizing.value, partialText.value],
  () => scrollToBottom(),
  { flush: 'post' }
)
watch(
  () => sessionId.value,
  () => scrollToBottom()
)

/**
 * 离开页面时释放音频/连接资源。
 * dispose() 内部：关闭 WS、pcmPlayer.close()、stopCurrentAudio()、清空 PCM 缓冲（幂等）。
 */
onBeforeUnmount(() => {
  disposeVoice()
})
</script>

<template>
  <div class="chat-page">
    <!-- 配置卡片：无会话时显示 -->
    <MockInterviewConfigCard
      v-if="!sessionId"
      v-model="selectedPersonaId"
      :personas="personas"
      :reference-written-test="referenceWrittenTest"
      :starting="starting"
      @toggle-reference-written-test="referenceWrittenTest = $event"
      @start="handleStart"
      @back="router.back()"
    />

    <!-- 聊天界面：已有会话时 -->
    <template v-else>
      <MockInterviewHeader
        :session-id="sessionId"
        :persona-name="currentPersonaName"
        :ended="ended"
        :outline="outline"
      />

      <MockInterviewMessageList
        ref="messageListRef"
        :messages="messages"
        :loading="loading"
        :thinking="thinking"
        :ended="ended"
        :recognizing="recognizing"
        :partial-text="partialText"
        :personas="personas"
        :persona-name="currentPersonaName"
        @speak="speak"
        @replay="playReplay"
      />

      <MockInterviewComposer
        v-model="textInput"
        :auto-tts="autoTts"
        :sending="sending"
        :recognizing="recognizing"
        :recording="recording"
        :ended="ended"
        :ending="ending"
        :session-id="sessionId"
        @toggle-auto-tts="autoTts = $event"
        @send="sendText"
        @record-start="handleRecStart"
        @record-chunk="onRecChunk"
        @record-recorded="onRecRecorded"
        @switch-persona="openSwitchDialog"
        @end="endInterview"
      />
    </template>

    <!-- 换面试官弹窗 -->
    <MockInterviewPersonaDialog
      v-model:visible="switchDialogVisible"
      :personas="personas"
      :persona-id="switchPersonaId"
      :switching="switching"
      @change-persona="setSwitchPersonaId"
      @confirm="confirmSwitch"
    />
  </div>
</template>

<style scoped>
.chat-page {
  height: 100vh;
  max-width: 900px;
  margin: 0 auto;
  display: flex;
  flex-direction: column;
  background: #f5f7fa;
  overflow: hidden;
}
</style>
