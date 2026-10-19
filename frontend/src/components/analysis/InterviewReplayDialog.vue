<script setup>
// 面试回看弹窗：完整对话 + 录音回放（纯展示 + 本地音频播放）
defineProps({
  // 对话消息列表
  messages: { type: Array, default: () => [] },
  // 消息加载中
  loading: { type: Boolean, default: false },
})

// 弹窗显隐由父组件控制
const visible = defineModel({ type: Boolean, default: false })

function formatDateTime(s) {
  if (!s) return ''
  const d = new Date(s)
  if (Number.isNaN(d.getTime())) return String(s)
  const p = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}`
}

// 回放对话录音
let replayAudio = null
function playReplay(audioPath) {
  if (!audioPath) return
  if (replayAudio) {
    replayAudio.pause()
    replayAudio = null
  }
  const audio = new Audio(`/api/voice/audio/${audioPath}`)
  replayAudio = audio
  audio.onended = () => {
    replayAudio = null
  }
  audio.play().catch(() => {
    replayAudio = null
  })
}
</script>

<template>
  <el-dialog v-model="visible" title="完整对话回看" width="680px" top="6vh">
    <div class="replay-box" v-loading="loading">
      <template v-if="!loading">
        <div v-if="messages.length" class="replay-list">
          <div
            v-for="(m, i) in messages"
            :key="m.id ? m.id : `local-${i}`"
            class="replay-row"
            :class="{ 'replay-candidate': m.role === 'CANDIDATE' }"
          >
            <div v-if="m.role === 'SYSTEM'" class="replay-system">{{ m.content }}</div>
            <template v-else>
              <div class="replay-bubble" :class="m.role === 'CANDIDATE' ? 'bub-candidate' : 'bub-interviewer'">
                <div class="replay-text">{{ m.content }}</div>
                <div class="replay-meta">
                  <span class="replay-role">{{ m.role === 'CANDIDATE' ? '候选人' : '面试官' }}</span>
                  <button v-if="m.audioPath" class="replay-play" @click="playReplay(m.audioPath)">🔊 回放</button>
                  <span class="replay-time">{{ formatDateTime(m.createdAt) }}</span>
                </div>
              </div>
            </template>
          </div>
        </div>
        <el-empty v-else description="暂无对话记录" :image-size="60" />
      </template>
    </div>
  </el-dialog>
</template>

<style scoped>
.replay-box {
  min-height: 200px;
  max-height: 62vh;
  overflow-y: auto;
}
.replay-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.replay-row {
  display: flex;
  justify-content: flex-start;
}
.replay-candidate {
  justify-content: flex-end;
}
.replay-system {
  margin: 0 auto;
  background: rgba(0, 0, 0, 0.05);
  color: #909399;
  font-size: 12px;
  padding: 3px 14px;
  border-radius: 999px;
  max-width: 85%;
  text-align: center;
}
.replay-bubble {
  max-width: 78%;
  border-radius: 10px;
  padding: 10px 14px;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.04);
}
.bub-interviewer {
  background: #303133;
  color: #fff;
  border-top-left-radius: 3px;
}
.bub-candidate {
  background: #e8f3ff;
  color: #303133;
  border-top-right-radius: 3px;
}
.replay-text {
  font-size: 14px;
  line-height: 1.7;
  white-space: pre-wrap;
  word-break: break-word;
}
.replay-meta {
  margin-top: 6px;
  display: flex;
  justify-content: space-between;
  gap: 12px;
  font-size: 11px;
}
.replay-play {
  border: none;
  background: none;
  cursor: pointer;
  font-size: 12px;
  color: #409eff;
  padding: 0;
}
.replay-play:hover {
  text-decoration: underline;
}
.bub-interviewer .replay-meta {
  color: rgba(255, 255, 255, 0.6);
}
.bub-candidate .replay-meta {
  color: #a0a6ad;
}
</style>
