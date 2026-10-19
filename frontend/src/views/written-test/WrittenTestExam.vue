<script setup>
/**
 * 笔试答题页外壳。
 *
 * 职责只剩三件：加载/恢复考试数据、倒计时、交卷。
 * 渲染拆到 QuestionItem（单题）与 AnswerCard（答题卡），纯函数在 examHelpers.js。
 * 拆之前这里是 586 行，改一个选项样式要在倒计时和交卷逻辑之间来回找。
 */
import { ref, reactive, computed, watch, onMounted, onBeforeUnmount } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { submitTest, getExamInProgress } from '../../api/written-test'
import { useWrittenTestStore } from '../../stores/writtenTest'
import QuestionItem from './QuestionItem.vue'
import AnswerCard from './AnswerCard.vue'
import { buildAnswers, isAnswered } from './examHelpers'

const route = useRoute()
const router = useRouter()
const store = useWrittenTestStore()

const sessionId = route.params.sessionId
const questions = ref([])
const answers = reactive({})
const remaining = ref(0)
const submitting = ref(false)
const loaded = ref(false)

// 答题内容持久化到 sessionStorage，刷新后可恢复（避免已答内容丢失）
const ANSWERS_KEY = `written-test:answers:${sessionId}`
function persistAnswers() {
  try {
    sessionStorage.setItem(ANSWERS_KEY, JSON.stringify(answers))
  } catch (e) {
    // 忽略写入失败
  }
}
function restoreAnswers() {
  try {
    const raw = sessionStorage.getItem(ANSWERS_KEY)
    if (raw) {
      const saved = JSON.parse(raw)
      for (const [k, v] of Object.entries(saved)) {
        if (k in answers) answers[k] = v
      }
    }
  } catch (e) {
    // 忽略损坏的缓存
  }
}
function clearAnswers() {
  try {
    sessionStorage.removeItem(ANSWERS_KEY)
  } catch (e) {
    // 忽略
  }
}
watch(answers, persistAnswers, { deep: true })

const qRefs = []

const answeredCount = computed(() => questions.value.filter((q) => isAnswered(q, answers)).length)

function formatTime(ms) {
  if (ms < 0) ms = 0
  const totalSec = Math.floor(ms / 1000)
  const h = Math.floor(totalSec / 3600)
  const m = Math.floor((totalSec % 3600) / 60)
  const s = totalSec % 60
  const pad = (n) => String(n).padStart(2, '0')
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

async function handleSubmit() {
  if (submitting.value) return
  submitting.value = true
  try {
    const res = await submitTest(sessionId, buildAnswers(questions.value, answers))
    if (res.code === 200) {
      // 服务端计时校验结果：超时提交照常出成绩，但明确告知用户成绩仅供参考
      if (res.data?.expired) {
        ElMessage.warning('本次交卷已超出考试时长，成绩仅供参考')
      }
      clearAnswers()
      const pid = store.session?.projectId
      const flow = store.session?.flow
      store.clear()
      if (flow === 'full') {
        // 完整流程：笔试完成 → 自动进入模拟面试（参考笔试结果）
        ElMessage.success('笔试完成，开始模拟面试！')
        router.push(`/mock-interview/${pid}?refWritten=1`)
      } else {
        router.push({ path: `/written-test/result/${sessionId}`, query: pid ? { projectId: pid } : {} })
      }
    } else {
      ElMessage.error(res.message || '提交失败')
    }
  } catch (e) {
    // request 拦截器已提示错误
  } finally {
    submitting.value = false
  }
}

function initExam(list, deadline) {
  questions.value = list
  // 初始化答案容器（多选为数组）
  for (const q of questions.value) {
    answers[q.id] = q.type === 'MULTIPLE_CHOICE' ? [] : ''
  }
  // 恢复刷新前已填写的答案
  restoreAnswers()
  loaded.value = true
  startCountdown(deadline)
}

onMounted(async () => {
  // 优先从本地缓存恢复（刚出完题的场景）
  const session = store.load()
  if (session && session.sessionId === sessionId && Array.isArray(session.questions)) {
    initExam(session.questions, session.deadline || Date.now() + session.timeLimit * 60000)
    return
  }
  // 缓存丢失（刷新/离开后继续）：从后端拉取进行中的笔试
  try {
    const res = await getExamInProgress(sessionId)
    if (res.code === 200 && res.data?.questions?.length) {
      const dto = res.data
      // 服务端下发的 deadline 为权威值；缺失时才退回本地推导
      const deadline = Number.isFinite(dto.deadline)
        ? dto.deadline
        : dto.startedAt
          ? dto.startedAt + (dto.timeLimit || 60) * 60000
          : Date.now() + (dto.timeLimit || 60) * 60000
      store.save({
        sessionId,
        projectId: dto.projectId || route.query.projectId || session?.projectId || null,
        timeLimit: dto.timeLimit,
        deadline,
        questions: dto.questions,
      })
      initExam(dto.questions, deadline)
      return
    }
  } catch (e) {
    // 拦截器已提示
  }
  ElMessage.warning('未找到有效的考试信息，请重新开始考试')
  router.replace('/')
})

onBeforeUnmount(stopCountdown)
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
            <QuestionItem
              v-for="(q, idx) in questions"
              :key="q.id"
              :ref="(el) => setQRef(idx, el)"
              :question="q"
              :index="idx"
              v-model="answers[q.id]"
            />
          </div>
        </el-scrollbar>
      </div>

      <!-- 答题卡 -->
      <AnswerCard
        :questions="questions"
        :answers="answers"
        :submitting="submitting"
        @goto="scrollTo"
        @submit="handleSubmit"
      />
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

/* 窄屏适配：答题卡移到题目下方，纵向滚动 */
@media (max-width: 768px) {
  .exam-page {
    height: auto;
    min-height: 100vh;
  }
  .exam-body {
    flex-direction: column;
    overflow: visible;
  }
  .question-area {
    order: 1;
  }
  .question-scroll {
    height: auto;
    max-height: 55vh;
  }
}
</style>
