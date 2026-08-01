<script setup>
import { ref, computed, reactive, onMounted, onBeforeUnmount } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { submitTest } from '../../api/written-test'
import { useWrittenTestStore } from '../../stores/writtenTest'

const route = useRoute()
const router = useRouter()
const store = useWrittenTestStore()

const sessionId = route.params.sessionId
const questions = ref([])
const answers = reactive({})
const remaining = ref(0)
const submitting = ref(false)
const loaded = ref(false)

const qRefs = []

function letter(index) {
  return String.fromCharCode(65 + index)
}

function parseOptions(q) {
  try {
    const arr = JSON.parse(q.options || '[]')
    return Array.isArray(arr) ? arr : []
  } catch (e) {
    return []
  }
}

function isAnswered(q) {
  const val = answers[q.id]
  if (q.type === 'MULTIPLE_CHOICE') {
    return Array.isArray(val) && val.length > 0
  }
  return val !== undefined && val !== null && String(val).trim() !== ''
}

const answeredCount = computed(() => questions.value.filter(q => isAnswered(q)).length)

const typeLabel = {
  SINGLE_CHOICE: '单选题',
  MULTIPLE_CHOICE: '多选题',
  FILL_BLANK: '填空题',
  SHORT_ANSWER: '简答题',
}

const typeTagType = {
  SINGLE_CHOICE: 'primary',
  MULTIPLE_CHOICE: 'warning',
  FILL_BLANK: 'success',
  SHORT_ANSWER: 'info',
}

function formatTime(ms) {
  if (ms < 0) ms = 0
  const totalSec = Math.floor(ms / 1000)
  const h = Math.floor(totalSec / 3600)
  const m = Math.floor((totalSec % 3600) / 60)
  const s = totalSec % 60
  const pad = n => String(n).padStart(2, '0')
  return `${pad(h)}:${pad(m)}:${pad(s)}`
}

const timeText = computed(() => formatTime(remaining.value))
const urgent = computed(() => remaining.value < 5 * 60 * 1000)

let timer = null
function startCountdown(deadline) {
  const tick = () => {
    remaining.value = deadline - Date.now()
    if (remaining.value <= 0) {
      stopCountdown()
      if (!submitting.value) {
        ElMessage.warning('考试时间到，系统已自动交卷')
        handleSubmit()
      }
    }
  }
  tick()
  timer = setInterval(tick, 1000)
}

function stopCountdown() {
  if (timer) {
    clearInterval(timer)
    timer = null
  }
}

function setQRef(index, el) {
  qRefs[index] = el
}

function scrollTo(index) {
  qRefs[index]?.scrollIntoView({ behavior: 'smooth', block: 'center' })
}

function buildAnswers() {
  const result = {}
  for (const q of questions.value) {
    const val = answers[q.id]
    if (q.type === 'MULTIPLE_CHOICE') {
      if (Array.isArray(val) && val.length > 0) {
        // 多选题答案按字母排序，如 "AB"
        result[q.id] = [...val].sort().join('')
      }
    } else if (val !== undefined && val !== null && String(val).trim() !== '') {
      result[q.id] = String(val).trim()
    }
  }
  return result
}

async function handleSubmit() {
  if (submitting.value) return
  submitting.value = true
  try {
    const res = await submitTest(sessionId, buildAnswers())
    if (res.code === 200) {
      const pid = store.session?.projectId
      store.clear()
      router.push({ path: `/written-test/result/${sessionId}`, query: pid ? { projectId: pid } : {} })
    } else {
      ElMessage.error(res.message || '提交失败')
    }
  } catch (e) {
    // request 拦截器已提示错误
  } finally {
    submitting.value = false
  }
}

onMounted(() => {
  const session = store.load()
  if (!session || session.sessionId !== sessionId || !Array.isArray(session.questions)) {
    ElMessage.warning('未找到有效的考试信息，请重新开始考试')
    router.replace('/')
    return
  }
  questions.value = session.questions
  // 初始化答案容器（多选为数组）
  for (const q of questions.value) {
    answers[q.id] = q.type === 'MULTIPLE_CHOICE' ? [] : ''
  }
  loaded.value = true
  startCountdown(session.deadline || Date.now() + session.timeLimit * 60000)
})

onBeforeUnmount(() => {
  stopCountdown()
})
</script>

<template>
  <div class="exam-page">
    <header class="exam-header">
      <div class="header-title">笔试答题</div>
      <div class="header-info">
        <span class="answered-info">
          已答 <b>{{ answeredCount }}</b> / {{ questions.length }}
        </span>
        <span :class="['countdown', { urgent }]">⏱ {{ timeText }}</span>
      </div>
    </header>

    <div class="exam-body">
      <!-- 题目区 -->
      <div class="question-area">
        <el-scrollbar class="question-scroll">
          <div v-if="loaded" class="question-list">
            <div
              v-for="(q, idx) in questions"
              :key="q.id"
              :ref="el => setQRef(idx, el)"
              class="question-item"
            >
              <div class="q-head">
                <span class="q-index">{{ idx + 1 }}</span>
                <el-tag :type="typeTagType[q.type]" size="small" effect="plain">
                  {{ typeLabel[q.type] || q.type }}
                </el-tag>
              </div>
              <div class="q-content">{{ q.content }}</div>

              <!-- 单选题 -->
              <el-radio-group v-if="q.type === 'SINGLE_CHOICE'" v-model="answers[q.id]" class="q-options">
                <el-radio
                  v-for="(opt, i) in parseOptions(q)"
                  :key="i"
                  :value="letter(i)"
                  class="q-option"
                >
                  <span class="opt-key">{{ letter(i) }}.</span> {{ opt }}
                </el-radio>
              </el-radio-group>

              <!-- 多选题 -->
              <el-checkbox-group v-else-if="q.type === 'MULTIPLE_CHOICE'" v-model="answers[q.id]" class="q-options">
                <el-checkbox
                  v-for="(opt, i) in parseOptions(q)"
                  :key="i"
                  :value="letter(i)"
                  class="q-option"
                >
                  <span class="opt-key">{{ letter(i) }}.</span> {{ opt }}
                </el-checkbox>
              </el-checkbox-group>

              <!-- 填空题 -->
              <div v-else-if="q.type === 'FILL_BLANK'" class="q-input">
                <el-input v-model="answers[q.id]" placeholder="请输入你的答案" clearable />
              </div>

              <!-- 简答题 -->
              <div v-else-if="q.type === 'SHORT_ANSWER'" class="q-input">
                <el-input
                  v-model="answers[q.id]"
                  type="textarea"
                  :rows="4"
                  placeholder="请输入你的回答"
                  show-word-limit
                  maxlength="2000"
                />
              </div>
            </div>
          </div>
        </el-scrollbar>
      </div>

      <!-- 答题卡 -->
      <div class="answer-card">
        <el-card shadow="never" class="card">
          <div class="card-title">答题卡</div>
          <div class="grid">
            <div
              v-for="(q, idx) in questions"
              :key="q.id"
              :class="['num', { answered: isAnswered(q) }]"
              @click="scrollTo(idx)"
            >
              {{ idx + 1 }}
            </div>
          </div>
          <div class="legend">
            <span class="legend-item"><span class="dot answered"></span>已答</span>
            <span class="legend-item"><span class="dot"></span>未答</span>
          </div>
          <el-popconfirm
            title="确认要交卷吗？"
            confirm-button-text="确认交卷"
            cancel-button-text="再检查一下"
            width="220"
            @confirm="handleSubmit"
          >
            <template #reference>
              <el-button type="primary" class="submit-btn" :loading="submitting" style="width: 100%">
                交卷
              </el-button>
            </template>
          </el-popconfirm>
        </el-card>
      </div>
    </div>
  </div>
</template>

<style scoped>
.exam-page {
  height: 100vh;
  display: flex;
  flex-direction: column;
  background: #f5f7fa;
}
.exam-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 24px;
  height: 56px;
  background: #fff;
  border-bottom: 1px solid #e4e7ed;
  flex-shrink: 0;
}
.header-title {
  font-size: 18px;
  font-weight: 600;
}
.header-info {
  display: flex;
  align-items: center;
  gap: 20px;
}
.answered-info {
  color: #606266;
  font-size: 14px;
}
.answered-info b {
  color: #409eff;
}
.countdown {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
  font-variant-numeric: tabular-nums;
}
.countdown.urgent {
  color: #f56c6c;
  animation: blink 1s step-start infinite;
}
@keyframes blink {
  50% {
    opacity: 0.5;
  }
}

.exam-body {
  flex: 1;
  display: flex;
  gap: 16px;
  padding: 16px;
  min-height: 0;
}
.question-area {
  flex: 1;
  min-width: 0;
}
.question-scroll {
  height: 100%;
  background: #fff;
  border-radius: 8px;
  border: 1px solid #e4e7ed;
}
.question-list {
  padding: 16px 20px;
}
.question-item {
  padding: 20px 0;
  border-bottom: 1px solid #f0f2f5;
}
.question-item:last-child {
  border-bottom: none;
}
.q-head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
}
.q-index {
  width: 26px;
  height: 26px;
  border-radius: 50%;
  background: #409eff;
  color: #fff;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 13px;
  flex-shrink: 0;
}
.q-content {
  font-size: 15px;
  line-height: 1.6;
  color: #303133;
  margin-bottom: 14px;
  white-space: pre-wrap;
  word-break: break-word;
}
.q-options {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 10px;
}
.q-option {
  display: flex;
  align-items: flex-start;
  height: auto;
  white-space: normal;
  line-height: 1.5;
}
.opt-key {
  font-weight: 600;
  margin-right: 2px;
}
.q-input {
  max-width: 600px;
}

/* 答题卡 */
.answer-card {
  width: 230px;
  flex-shrink: 0;
}
.card {
  border-radius: 8px;
}
.card-title {
  font-size: 15px;
  font-weight: 600;
  margin-bottom: 12px;
}
.grid {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 8px;
  margin-bottom: 12px;
}
.num {
  aspect-ratio: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  border: 1px solid #dcdfe6;
  border-radius: 6px;
  font-size: 13px;
  color: #606266;
  cursor: pointer;
  transition: all 0.2s;
  background: #fff;
}
.num:hover {
  border-color: #409eff;
  color: #409eff;
}
.num.answered {
  background: #409eff;
  border-color: #409eff;
  color: #fff;
}
.legend {
  display: flex;
  gap: 16px;
  margin-bottom: 16px;
  font-size: 13px;
  color: #606266;
}
.legend-item {
  display: inline-flex;
  align-items: center;
  gap: 5px;
}
.dot {
  width: 12px;
  height: 12px;
  border-radius: 3px;
  border: 1px solid #dcdfe6;
  display: inline-block;
  background: #fff;
}
.dot.answered {
  background: #409eff;
  border-color: #409eff;
}
</style>
