<script setup>
/**
 * 消息气泡列表：候选人 / 面试官气泡、系统提示、思考中动画、实时识别中间结果。
 * 滚动容器由本组件持有，通过 defineExpose 暴露 scrollToBottom() 给父组件在流式增量时调用。
 */
import { nextTick, ref } from 'vue'

const props = defineProps({
  messages: { type: Array, default: () => [] }, // 消息列表
  loading: { type: Boolean, default: false }, // 历史消息加载中
  thinking: { type: Boolean, default: false }, // 面试官思考中
  ended: { type: Boolean, default: false }, // 面试是否已结束
  recognizing: { type: Boolean, default: false }, // STT 识别中
  partialText: { type: String, default: '' }, // 流式 ASR 中间识别文本
  personas: { type: Array, default: () => [] }, // 面试官风格列表（解析气泡头像首字）
  personaName: { type: String, default: '面试官' }, // 当前面试官名称
})

const emit = defineEmits(['speak', 'replay'])

const listRef = ref(null)

function avatarOf(msg) {
  if (msg.role === 'CANDIDATE') return '我'
  if (msg.personaId) {
    const p = props.personas.find((x) => x.id === msg.personaId)
    if (p?.name) return p.name.charAt(0)
  }
  return (props.personaName || '面').charAt(0)
}

function formatTime(createdAt) {
  if (!createdAt) return ''
  const d = new Date(createdAt)
  if (Number.isNaN(d.getTime())) return ''
  const hh = d.getHours().toString().padStart(2, '0')
  const mm = d.getMinutes().toString().padStart(2, '0')
  return `${hh}:${mm}`
}

// 消息列表滚动到底部（供父组件在流式增量 / 新消息时调用）
function scrollToBottom() {
  nextTick(() => {
    if (listRef.value) listRef.value.scrollTop = listRef.value.scrollHeight
  })
}

defineExpose({ scrollToBottom })
</script>

<template>
  <div ref="listRef" class="message-list" v-loading="loading">
    <template v-if="!loading">
      <!-- 消息列表 -->
      <div
        v-for="(m, i) in messages"
        :key="m.id ? m.id : `local-${i}`"
        class="msg-row"
        :class="{ 'msg-candidate': m.role === 'CANDIDATE' }"
      >
        <div v-if="m.role === 'SYSTEM'" class="system-msg">{{ m.content }}</div>
        <template v-else-if="m.role === 'INTERVIEWER'">
          <div class="avatar avatar-interviewer">{{ avatarOf(m) }}</div>
          <div class="bubble-group">
            <div
              class="bubble bubble-interviewer"
              :class="{ 'bubble-ended': m.content?.startsWith('【面试结束】') }"
            >
              <div class="bubble-text">{{ m.content }}</div>
              <div class="bubble-meta">
                <span class="time">{{ formatTime(m.createdAt) }}</span>
                <button
                  class="link-btn"
                  v-if="m.content"
                  @click="emit('speak', m.content)"
                  title="朗读本条回复"
                >
                  🔊 朗读
                </button>
                <button
                  class="link-btn"
                  v-if="m.audioPath"
                  @click="emit('replay', m.audioPath)"
                  title="回放录音"
                >
                  🔊 回放
                </button>
              </div>
            </div>
          </div>
        </template>
        <template v-else>
          <div class="bubble-group">
            <div class="bubble bubble-candidate">
              <div class="bubble-text">{{ m.content }}</div>
              <div class="bubble-meta meta-candidate">
                <span class="time">{{ formatTime(m.createdAt) }}</span>
                <button class="link-btn" v-if="m.audioPath" @click="emit('replay', m.audioPath)">
                  🔊 回放
                </button>
              </div>
            </div>
          </div>
          <div class="avatar avatar-candidate">{{ avatarOf(m) }}</div>
        </template>
      </div>

      <!-- 思考中动画 -->
      <div v-if="thinking && !ended" class="msg-row">
        <div class="avatar avatar-interviewer">{{ personaName.charAt(0) }}</div>
        <div class="bubble bubble-interviewer thinking-bubble">
          <span class="thinking-text">面试官正在思考</span>
          <span class="dots">
            <span class="dot-i" />
            <span class="dot-i" />
            <span class="dot-i" />
          </span>
        </div>
      </div>

      <!-- 实时识别中（用户说话内容的中间结果，固定在消息区展示，避免撑动输入区高度） -->
      <div v-if="recognizing || partialText" class="msg-row msg-candidate">
        <div class="bubble-group">
          <div class="bubble bubble-candidate recognizing-bubble">
            <span class="recognizing-dot" />
            <span class="recognizing-text">{{ partialText || '正在识别…' }}</span>
          </div>
        </div>
      </div>
    </template>
  </div>
</template>

<style scoped>
.message-list {
  flex: 1;
  overflow-y: auto;
  padding: 18px 20px;
  display: flex;
  flex-direction: column;
  gap: 14px;
}
.msg-row {
  display: flex;
  align-items: flex-end;
  gap: 10px;
  width: 100%;
}
.msg-candidate {
  justify-content: flex-end;
}
.avatar {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 15px;
  font-weight: 600;
  color: #fff;
  flex-shrink: 0;
}
.avatar-interviewer { background: #303133; }
.avatar-candidate { background: #409eff; }
.bubble-group {
  max-width: 72%;
  display: flex;
  flex-direction: column;
}
.bubble {
  border-radius: 12px;
  padding: 10px 14px;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.05);
}
.bubble-interviewer {
  background: #fff;
  border-top-left-radius: 4px;
}
.bubble-candidate {
  background: #e8f3ff;
  border-top-right-radius: 4px;
  align-self: flex-end;
}
.bubble-ended {
  border: 1px solid #67c23a;
  background: #f0f9eb;
}
.bubble-text {
  white-space: pre-wrap;
  word-break: break-word;
  line-height: 1.6;
  font-size: 14px;
}
.bubble-meta {
  margin-top: 6px;
  display: flex;
  align-items: center;
  gap: 10px;
}
.meta-candidate { justify-content: flex-end; }
.time { font-size: 11px; color: #b0b3b8; }
.link-btn {
  border: none;
  background: transparent;
  color: #409eff;
  font-size: 12px;
  cursor: pointer;
  padding: 0;
}
.link-btn:hover {
  text-decoration: underline;
}
.system-msg {
  margin: 0 auto;
  background: rgba(0, 0, 0, 0.04);
  color: #909399;
  font-size: 12px;
  padding: 4px 14px;
  border-radius: 999px;
  max-width: 80%;
  text-align: center;
}

/* 思考中动画 */
.thinking-bubble {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}
.thinking-text {
  font-size: 13px;
  color: #909399;
}
.dots {
  display: inline-flex;
  gap: 3px;
}
.dot-i {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: #909399;
  animation: bounce 1.2s infinite ease-in-out;
}
.dot-i:nth-child(2) {
  animation-delay: 0.15s;
}
.dot-i:nth-child(3) {
  animation-delay: 0.3s;
}
@keyframes bounce {
  0%,
  80%,
  100% {
    transform: translateY(0);
    opacity: 0.5;
  }
  40% {
    transform: translateY(-4px);
    opacity: 1;
  }
}

/* 实时识别 */
.recognizing-bubble {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  opacity: 0.9;
}
.recognizing-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #409eff;
  animation: recognizing-pulse 1s ease-in-out infinite;
  flex-shrink: 0;
}
.recognizing-text {
  font-size: 13px;
  color: #606266;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 55vw;
}
@keyframes recognizing-pulse {
  0%,
  100% {
    opacity: 0.35;
  }
  50% {
    opacity: 1;
  }
}
</style>
