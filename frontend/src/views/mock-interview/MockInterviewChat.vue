<script setup>
import { ref, computed, onMounted, watch, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import AudioRecorder from '../../components/AudioRecorder.vue'
import {
  startMockInterview,
  respondMockInterview,
  concludeMockInterview,
  getMockInterviewMessages,
  switchPersona,
  getPersonaList,
} from '../../api/mock-interview'
import { recognizeSpeech, synthesizeSpeech } from '../../api/voice'
import { blobToWav } from '../../utils/audio'

const route = useRoute()
const router = useRouter()

// ---------- 会话状态 ----------
const projectId = ref(route.params.projectId || null)
const sessionId = ref(route.query.sessionId || null)
const messages = ref([])
const outline = ref('')
const personaName = ref('')
const ended = ref(false)

// ---------- 流程状态 ----------
const loading = ref(false) // 历史加载
const starting = ref(false) // 开始面试
const sending = ref(false) // 回复发送中
const recognizing = ref(false) // STT 识别中
const ending = ref(false) // 结束面试
const switching = ref(false) // 换面试官
const thinking = computed(() => sending.value) // 面试官思考中

// ---------- 配置状态 ----------
const personas = ref([])
const selectedPersonaId = ref(null)
const referenceWrittenTest = ref(false)
const textInput = ref('')
const autoTts = ref(true)

// ---------- 换面试官 ----------
const switchDialogVisible = ref(false)
const switchPersonaId = ref(null)

const listRef = ref(null)

// ---------- 派生数据 ----------
const currentPersonaId = computed(() => {
  for (let i = messages.value.length - 1; i >= 0; i--) {
    const m = messages.value[i]
    if (m.role === 'INTERVIEWER' && m.personaId) return m.personaId
  }
  return null
})

const currentPersonaName = computed(() => {
  const p = personas.value.find((x) => x.id === currentPersonaId.value)
  return p?.name || personaName.value || '面试官'
})

const currentPersonaStyle = computed(() => {
  const p = personas.value.find((x) => x.id === currentPersonaId.value)
  return p?.styleConfig || null
})

// 大纲解析：后端返回 JSON 字符串，剥离可能的 markdown 代码块
const outlineSections = computed(() => {
  if (!outline.value) return []
  try {
    let obj
    try {
      obj = JSON.parse(outline.value)
    } catch {
      obj = JSON.parse(outline.value.replace(/```(json)?/g, '').trim())
    }
    if (!obj || typeof obj !== 'object') return []
    const list = []
    if (Array.isArray(obj.sections)) {
      const labels = { TECHNICAL: '技术能力', PROJECT: '项目深挖', BEHAVIORAL: '行为面试' }
      obj.sections.forEach((s) => {
        list.push({
          type: labels[s.type] || s.type || '面试环节',
          topics: Array.isArray(s.topics) ? s.topics : [],
        })
      })
    }
    if (obj.estimatedQuestions) list.push({ type: '预计题量', topics: [`约 ${obj.estimatedQuestions} 题`] })
    if (Array.isArray(obj.focusPoints) && obj.focusPoints.length) {
      list.push({ type: '重点考察', topics: obj.focusPoints })
    }
    return list
  } catch {
    return []
  }
})

// ---------- 初始化 ----------
onMounted(async () => {
  await loadPersonas()
  if (sessionId.value) {
    await loadHistory()
  }
})

async function loadPersonas() {
  try {
    const res = await getPersonaList()
    const list = res?.data || []
    personas.value = Array.isArray(list) ? list : []
    if (personas.value.length && !selectedPersonaId.value) {
      const preset = personas.value.find((p) => p.isPreset) || personas.value[0]
      selectedPersonaId.value = preset.id
    }
  } catch {
    // 拦截器已提示
  }
}

async function loadHistory() {
  if (!sessionId.value) return
  loading.value = true
  try {
    const res = await getMockInterviewMessages(sessionId.value)
    const list = res?.data || []
    messages.value = Array.isArray(list) ? list : []
    // 当前面试官：取最近一条带 personaId 的面试官消息
    for (let i = messages.value.length - 1; i >= 0; i--) {
      const m = messages.value[i]
      if (m.role === 'INTERVIEWER' && m.personaId) {
        const p = personas.value.find((x) => x.id === m.personaId)
        if (p) personaName.value = p.name
        break
      }
    }
    ended.value = messages.value.some(
      (m) => m.role === 'INTERVIEWER' && m.content?.startsWith('【面试结束】')
    )
  } catch {
    // 拦截器已提示
  } finally {
    loading.value = false
  }
}

// ---------- 开始面试 ----------
async function handleStart() {
  if (!projectId.value) {
    ElMessage.error('缺少项目信息，无法开始面试')
    return
  }
  if (starting.value) return
  starting.value = true
  try {
    const res = await startMockInterview(projectId.value, {
      personaId: selectedPersonaId.value || null,
      referenceWrittenTest: referenceWrittenTest.value,
    })
    const data = res?.data
    if (!data?.sessionId) {
      ElMessage.error(res?.message || '开始面试失败')
      return
    }
    sessionId.value = data.sessionId
    personaName.value = data.personaName
    outline.value = data.outline || ''
    messages.value = [
      {
        id: null,
        role: 'INTERVIEWER',
        content: data.openingMessage,
        personaId: data.personaId,
        createdAt: new Date(),
      },
    ]
    // 把 sessionId 写入 URL，刷新后可恢复历史
    router.replace({ path: `/mock-interview/${projectId.value}`, query: { sessionId: sessionId.value } })
  } catch {
    // 拦截器已提示
  } finally {
    starting.value = false
  }
}

// ---------- 发送流程 ----------
function sendText() {
  const text = textInput.value.trim()
  if (!text) {
    ElMessage.warning('请先输入内容')
    return
  }
  if (sending.value || recognizing.value) return
  textInput.value = ''
  doSend(text)
}

// 语音录音结束 → STT → respond
async function handleRecorded(blob) {
  if (sending.value || recognizing.value || ended.value) return
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
    doSend(text)
  } catch (e) {
    recognizing.value = false
    ElMessage.error(`语音识别失败：${e.message || '未知错误'}，请改用文字输入`)
  }
}

async function doSend(text) {
  if (!sessionId.value) return
  // 乐观插入候选人消息，即时反馈
  const optimistic = {
    id: null,
    role: 'CANDIDATE',
    content: text,
    createdAt: new Date(),
  }
  messages.value.push(optimistic)
  sending.value = true
  try {
    const res = await respondMockInterview(sessionId.value, { userText: text })
    const data = res?.data
    if (!data?.aiMessage) {
      ElMessage.error(res?.message || '面试官回复失败')
      messages.value = messages.value.filter((m) => m !== optimistic)
      return
    }
    // 用服务端保存的消息替换乐观消息，并追加 AI 回复
    const idx = messages.value.indexOf(optimistic)
    if (idx >= 0) messages.value[idx] = data.userMessage || optimistic
    else messages.value.push(data.userMessage)
    messages.value.push(data.aiMessage)

    const aiContent = data.aiMessage.content || ''
    if (aiContent.startsWith('【面试结束】')) {
      ended.value = true
      handleInterviewEnded()
    } else if (autoTts.value) {
      speak(aiContent)
    }
  } catch {
    // 失败：把文本放回输入框，方便重发
    messages.value = messages.value.filter((m) => m !== optimistic)
    textInput.value = text
  } finally {
    sending.value = false
  }
}

// ---------- 结束判定 ----------
async function handleInterviewEnded() {
  try {
    await ElMessageBox.confirm('面试官已宣布面试结束，是否现在查看面试分析？', '面试结束', {
      confirmButtonText: '查看分析',
      cancelButtonText: '留在本页',
      type: 'success',
    })
    router.push(`/analysis/session/${sessionId.value}`)
  } catch {
    // 用户选择留在本页
  }
}

// ---------- 手动结束 ----------
async function endInterview() {
  if (!sessionId.value || ended.value || ending.value) return
  try {
    await ElMessageBox.confirm('确定结束本次面试吗？系统将生成面试总结。', '结束面试', {
      confirmButtonText: '结束面试',
      cancelButtonText: '再想想',
      type: 'warning',
    })
  } catch {
    return
  }
  ending.value = true
  try {
    const res = await concludeMockInterview(sessionId.value)
    const summary = res?.data
    if (summary?.content) messages.value.push(summary)
    ended.value = true
    ElMessage.success('面试已结束，总结已生成')
    try {
      await ElMessageBox.alert('面试总结已生成，点击查看面试分析。', '面试完成', {
        confirmButtonText: '查看分析',
      })
      router.push(`/analysis/session/${sessionId.value}`)
    } catch {
      // 用户关闭弹窗，留在本页
    }
  } catch {
    // 拦截器已提示
  } finally {
    ending.value = false
  }
}

// ---------- 换面试官 ----------
function openSwitchDialog() {
  if (!sessionId.value || switching.value) return
  switchPersonaId.value = currentPersonaId.value
  switchDialogVisible.value = true
}

async function confirmSwitch() {
  if (!switchPersonaId.value) {
    ElMessage.warning('请选择新的面试官风格')
    return
  }
  switching.value = true
  try {
    await switchPersona(sessionId.value, switchPersonaId.value)
    const p = personas.value.find((x) => x.id === switchPersonaId.value)
    if (p) personaName.value = p.name
    messages.value.push({
      id: null,
      role: 'SYSTEM',
      content: `面试官已切换为【${personaName.value}】`,
      createdAt: new Date(),
    })
    ElMessage.success('面试官已切换')
    switchDialogVisible.value = false
  } catch {
    // 拦截器已提示
  } finally {
    switching.value = false
  }
}

// ---------- 语音播放 ----------
let currentAudio = null
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
  try {
    const blob = await synthesizeSpeech(cleanText, currentPersonaStyle.value)
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
      ElMessage.error('语音播放失败')
    }
    await audio.play()
  } catch {
    ElMessage.error('语音合成失败，请稍后重试')
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
function ttsText(content = '') {
  return content.replace(/^【面试结束】/, '').trim()
}

// ---------- 展示辅助 ----------
function avatarOf(msg) {
  if (msg.role === 'CANDIDATE') return '我'
  if (msg.personaId) {
    const p = personas.value.find((x) => x.id === msg.personaId)
    if (p?.name) return p.name.charAt(0)
  }
  return (currentPersonaName.value || '面').charAt(0)
}

function formatTime(createdAt) {
  if (!createdAt) return ''
  const d = new Date(createdAt)
  if (Number.isNaN(d.getTime())) return ''
  const hh = d.getHours().toString().padStart(2, '0')
  const mm = d.getMinutes().toString().padStart(2, '0')
  return `${hh}:${mm}`
}

function formatOutlineRow(content) {
  if (!content) return ''
  return content.replace(/^[-*•]\s*/, '')
}

// ---------- 自动滚动 ----------
function scrollToBottom() {
  nextTick(() => {
    if (listRef.value) listRef.value.scrollTop = listRef.value.scrollHeight
  })
}
watch(
  () => [messages.value.length, thinking.value, recognizing.value],
  () => scrollToBottom(),
  { flush: 'post' }
)
watch(
  () => sessionId.value,
  () => scrollToBottom()
)
</script>

<template>
  <div class="chat-page">
    <!-- 配置卡片：无会话时显示 -->
    <div v-if="!sessionId" class="config-wrap">
      <div class="config-card">
        <h2 class="config-title">AI 模拟面试</h2>
        <p class="config-subtitle">与 AI 面试官一对一对话，支持语音回答</p>

        <el-form label-width="90px" label-position="left" class="config-form">
          <el-form-item label="面试官风格">
            <el-select
              v-model="selectedPersonaId"
              placeholder="选择面试官风格"
              style="width: 100%"
              clearable
            >
              <el-option
                v-for="p in personas"
                :key="p.id"
                :label="p.name"
                :value="p.id"
              >
                <span>{{ p.name }}</span>
                <span class="option-desc">{{ p.description || '' }}</span>
              </el-option>
              <template v-if="!personas.length">
                <el-option value="" label="系统推荐" disabled />
              </template>
            </el-select>
            <div v-if="!personas.length" class="form-tip">未获取到面试官列表，将使用系统推荐风格</div>
          </el-form-item>
          <el-form-item label="笔试结合">
            <el-checkbox v-model="referenceWrittenTest">
              引用最近一次笔试结果，针对薄弱知识点重点考察
            </el-checkbox>
          </el-form-item>
        </el-form>

        <div class="config-actions">
          <el-button @click="router.back()">返回</el-button>
          <el-button type="primary" :loading="starting" @click="handleStart">开始面试</el-button>
        </div>
      </div>
    </div>

    <!-- 聊天界面：已有会话时 -->
    <template v-else>
      <header class="chat-header">
        <div class="header-left">
          <h2 class="header-title">AI 模拟面试</h2>
          <el-tag size="small" effect="dark" type="primary">{{ currentPersonaName }}</el-tag>
          <el-tag v-if="ended" size="small" type="success" effect="dark">已结束</el-tag>
        </div>
        <div class="header-right">
          <span class="session-id" v-if="sessionId">会话 #{{ sessionId }}</span>
        </div>
      </header>

      <!-- 大纲折叠面板 -->
      <div class="outline-wrap" v-if="outline">
        <el-collapse class="outline-collapse">
          <el-collapse-item>
            <template #title>
              <span class="outline-title">📋 本次面试大纲</span>
            </template>
            <div class="outline-body">
              <template v-if="outlineSections.length">
                <div v-for="(sec, i) in outlineSections" :key="i" class="outline-sec">
                  <div class="outline-type">{{ sec.type }}</div>
                  <ul class="outline-topics">
                    <li v-for="(t, j) in sec.topics" :key="j">{{ formatOutlineRow(t) }}</li>
                  </ul>
                </div>
              </template>
              <div v-else class="outline-raw">{{ outline }}</div>
            </div>
          </el-collapse-item>
        </el-collapse>
      </div>

      <!-- 消息区 -->
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
                      @click="speak(m.content)"
                      title="朗读本条回复"
                    >
                      🔊 朗读
                    </button>
                    <button
                      class="link-btn"
                      v-if="m.audioPath"
                      @click="playReplay(m.audioPath)"
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
                    <button class="link-btn" v-if="m.audioPath" @click="playReplay(m.audioPath)">
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
            <div class="avatar avatar-interviewer">{{ currentPersonaName.charAt(0) }}</div>
            <div class="bubble bubble-interviewer thinking-bubble">
              <span class="thinking-text">面试官正在思考</span>
              <span class="dots">
                <span class="dot-i" />
                <span class="dot-i" />
                <span class="dot-i" />
              </span>
            </div>
          </div>
        </template>
      </div>

      <!-- 输入区 -->
      <footer class="input-area" :class="{ disabled: ended }">
        <div v-if="ended" class="ended-banner">面试已结束，感谢参与</div>
        <div class="input-row">
          <AudioRecorder
            :class="{ 'input-disabled': sending || recognizing || ended }"
            @recorded="handleRecorded"
          />
          <el-input
            v-model="textInput"
            class="text-input"
            type="textarea"
            :rows="1"
            resize="none"
            placeholder="输入你的回答，或按住左侧按钮说话…"
            :disabled="sending || recognizing || ended"
            @keyup.enter.exact.prevent="sendText"
          />
          <el-button
            type="primary"
            :loading="sending"
            :disabled="recognizing || ended"
            @click="sendText"
          >
            发送
          </el-button>
        </div>
        <div v-if="recognizing" class="status-line status-recognizing">识别中…</div>
        <div class="tool-row">
          <el-switch
            v-model="autoTts"
            size="small"
            active-text="自动朗读面试官回复"
            :disabled="ended"
          />
          <div class="tool-spacer" />
          <el-button size="small" @click="openSwitchDialog" :disabled="!sessionId || ended">
            换面试官
          </el-button>
          <el-button size="small" type="danger" plain :disabled="!sessionId || ended" :loading="ending" @click="endInterview">
            结束面试
          </el-button>
        </div>
      </footer>
    </template>

    <!-- 换面试官弹窗 -->
    <el-dialog v-model="switchDialogVisible" title="切换面试官" width="420px">
      <p class="dialog-tip">切换后，后续提问将按新面试官的风格进行。</p>
      <el-select v-model="switchPersonaId" placeholder="选择新的面试官风格" style="width: 100%">
        <el-option v-for="p in personas" :key="p.id" :label="p.name" :value="p.id">
          <span>{{ p.name }}</span>
          <span class="option-desc">{{ p.description || '' }}</span>
        </el-option>
      </el-select>
      <template #footer>
        <el-button @click="switchDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="switching" @click="confirmSwitch">确认切换</el-button>
      </template>
    </el-dialog>
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

/* ---------- 配置卡片 ---------- */
.config-wrap {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
}
.config-card {
  width: 520px;
  background: #fff;
  border-radius: 12px;
  padding: 36px 36px 28px;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.08);
}
.config-title {
  margin: 0 0 8px;
  font-size: 22px;
  text-align: center;
}
.config-subtitle {
  margin: 0 0 28px;
  text-align: center;
  color: #909399;
  font-size: 13px;
}
.config-form {
  margin-bottom: 8px;
}
.form-tip {
  font-size: 12px;
  color: #909399;
  margin-top: 6px;
}
.config-actions {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}
.option-desc {
  float: right;
  font-size: 12px;
  color: #909399;
  margin-left: 16px;
  max-width: 180px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* ---------- 头部 ---------- */
.chat-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 20px;
  background: #fff;
  border-bottom: 1px solid #ebeef5;
  flex-shrink: 0;
}
.header-left {
  display: flex;
  align-items: center;
  gap: 10px;
}
.header-title {
  margin: 0;
  font-size: 17px;
}
.header-right .session-id {
  font-size: 12px;
  color: #909399;
}

/* ---------- 大纲 ---------- */
.outline-wrap {
  flex-shrink: 0;
  background: #fff;
  border-bottom: 1px solid #ebeef5;
}
.outline-collapse {
  border: none;
}
.outline-collapse :deep(.el-collapse-item__header) {
  background: transparent;
  height: 40px;
  padding-left: 20px;
}
.outline-title {
  font-weight: 600;
  color: #303133;
}
.outline-body {
  padding: 4px 24px 14px;
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}
.outline-sec {
  border: 1px solid #ebeef5;
  border-radius: 8px;
  padding: 8px 12px;
  min-width: 200px;
  background: #fafbfc;
}
.outline-type {
  font-weight: 600;
  font-size: 13px;
  color: #409eff;
  margin-bottom: 4px;
}
.outline-topics {
  margin: 0;
  padding-left: 18px;
  font-size: 12px;
  color: #606266;
}
.outline-raw {
  font-size: 12px;
  color: #606266;
  white-space: pre-wrap;
  width: 100%;
}

/* ---------- 消息区 ---------- */
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
.avatar-interviewer {
  background: #303133;
}
.avatar-candidate {
  background: #409eff;
}
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
.meta-candidate {
  justify-content: flex-end;
}
.time {
  font-size: 11px;
  color: #b0b3b8;
}
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

/* ---------- 输入区 ---------- */
.input-area {
  flex-shrink: 0;
  background: #fff;
  border-top: 1px solid #ebeef5;
  padding: 10px 16px 12px;
  position: relative;
}
.ended-banner {
  text-align: center;
  color: #67c23a;
  font-size: 13px;
  font-weight: 600;
  margin-bottom: 8px;
}
.input-row {
  display: flex;
  align-items: center;
  gap: 10px;
}
.text-input {
  flex: 1;
}
.input-disabled {
  opacity: 0.5;
  pointer-events: none;
}
.status-line {
  font-size: 12px;
  color: #e6a23c;
  margin-top: 4px;
  text-align: center;
}
.tool-row {
  margin-top: 8px;
  display: flex;
  align-items: center;
  gap: 8px;
}
.tool-spacer {
  flex: 1;
}
.dialog-tip {
  margin: 0 0 12px;
  color: #909399;
  font-size: 13px;
}
</style>
