<script setup>
import { ref, onBeforeUnmount } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { WarningFilled } from '@element-plus/icons-vue'
import { startTest } from '../../api/written-test'
import { useWrittenTestStore } from '../../stores/writtenTest'

const route = useRoute()
const router = useRouter()
const store = useWrittenTestStore()
const projectId = route.params.projectId

const timeLimit = ref(60)
const questionCount = ref(12)

// 出题等待：等待超时阈值（秒），超过则中止并提示重试
const WAIT_TIMEOUT = 90
const waiting = ref(false) // 是否正在等待 AI 出题
const elapsed = ref(0) // 已等待秒数
const phaseText = ref('正在连接 AI 模型…')
const failed = ref(false) // 出题失败/超时
const failReason = ref('')

let timer = null
let controller = null

function startTimer() {
  elapsed.value = 0
  timer = setInterval(() => {
    elapsed.value += 1
    if (elapsed.value <= 3) {
      phaseText.value = '正在连接 AI 模型…'
    } else {
      phaseText.value = `AI 正在生成题目（${questionCount.value} 道），通常需要 20-60 秒…`
    }
    // 达到阈值自动中止（进入失败态，由用户决定重试）
    if (elapsed.value >= WAIT_TIMEOUT && waiting.value) {
      abortByTimeout()
    }
  }, 1000)
}

function stopTimer() {
  if (timer) {
    clearInterval(timer)
    timer = null
  }
}

function fail(message) {
  stopTimer()
  waiting.value = false
  failed.value = true
  failReason.value = message
}

function abortByTimeout() {
  if (controller) {
    controller.abort()
  }
  fail(`AI 出题超过 ${WAIT_TIMEOUT} 秒无响应，请重试`)
}

async function handleStart() {
  // 再次点击（重试）：重置状态
  waiting.value = true
  failed.value = false
  failReason.value = ''
  controller = new AbortController()
  startTimer()
  try {
    const res = await startTest(
      projectId,
      { timeLimit: timeLimit.value, questionCount: questionCount.value },
      controller.signal
    )
    // 正常返回（可能因 abort 抛错，此处只处理成功分支）
    if (res.code === 200 && res.data?.sessionId) {
      store.save({
        sessionId: res.data.sessionId,
        projectId,
        timeLimit: res.data.timeLimit,
        deadline: Date.now() + res.data.timeLimit * 60000,
        questions: res.data.questions || [],
      })
      stopTimer()
      waiting.value = false
      ElMessage.success('考试开始，祝你好运！')
      router.push(`/written-test/exam/${res.data.sessionId}`)
    } else {
      fail(res.message || 'AI 出题失败，请重试')
    }
  } catch (e) {
    // 主动中止（超时）时不重复提示，拦截器对 cancel 也不应弹错
    if (e?.code === 'ERR_CANCELED' || e?.message?.includes('canceled')) {
      fail(`AI 出题超过 ${WAIT_TIMEOUT} 秒无响应，请重试`)
    } else {
      fail(`AI 出题失败：${e?.message || '网络异常'}，请重试`)
    }
  }
}

function goBack() {
  router.back()
}

onBeforeUnmount(() => {
  stopTimer()
  if (controller) controller.abort()
})
</script>

<template>
  <div class="start-page">
    <div class="start-card">
      <h2 class="title">AI 笔试考试</h2>
      <p class="subtitle">项目 ID：<el-tag type="info">{{ projectId }}</el-tag></p>

      <!-- 等待 AI 出题：进度提示 -->
      <div v-if="waiting && !failed" class="generating-panel">
        <el-progress
          type="circle"
          :percentage="Math.min(100, Math.round((elapsed / WAIT_TIMEOUT) * 100))"
          :width="110"
          :stroke-width="8"
          :status="elapsed > WAIT_TIMEOUT * 0.7 ? 'warning' : undefined"
        />
        <div class="gen-text">{{ phaseText }}</div>
        <div class="gen-elapsed">已等待 {{ elapsed }} 秒（超时 {{ WAIT_TIMEOUT }} 秒）</div>
        <div class="gen-tip">首次出题需要调用 AI 生成题目与解析，请耐心等待；若超时可按提示重试。</div>
      </div>

      <!-- 出题失败/超时 -->
      <div v-else-if="failed" class="fail-panel">
        <el-icon :size="44" color="#f56c6c"><WarningFilled /></el-icon>
        <div class="fail-text">{{ failReason }}</div>
        <div class="fail-actions">
          <el-button @click="goBack">返回</el-button>
          <el-button type="primary" :loading="waiting" @click="handleStart">重新尝试</el-button>
        </div>
      </div>

      <!-- 配置表单（等待中禁用） -->
      <template v-else>
        <el-form label-width="100px" label-position="left" class="config-form">
          <el-form-item label="考试时长">
            <el-input-number v-model="timeLimit" :min="10" :max="180" :step="5" :disabled="waiting" />
            <span class="unit">分钟</span>
          </el-form-item>
          <el-form-item label="题目数量">
            <el-input-number v-model="questionCount" :min="5" :max="30" :step="1" :disabled="waiting" />
            <span class="unit">道</span>
          </el-form-item>
        </el-form>

        <p class="tip">包含单选题、多选题、填空题与简答题，请合理安排答题时间。</p>

        <div class="actions">
          <el-button @click="goBack">返回上一页</el-button>
          <el-button type="primary" :loading="waiting" @click="handleStart">开始考试</el-button>
        </div>
      </template>
    </div>
  </div>
</template>

<style scoped>
.start-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #f5f7fa;
  padding: 24px;
}
.start-card {
  width: 460px;
  background: #fff;
  border-radius: 12px;
  padding: 40px;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.08);
}
.title {
  margin: 0 0 8px;
  font-size: 22px;
  text-align: center;
}
.subtitle {
  margin: 0 0 24px;
  text-align: center;
  color: #606266;
  font-size: 14px;
}
.config-form {
  margin-bottom: 8px;
}
.unit {
  margin-left: 8px;
  color: #909399;
  font-size: 14px;
}
.tip {
  margin: 0 0 24px;
  font-size: 12px;
  color: #909399;
}
.actions {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}
.generating-panel {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 14px;
  padding: 12px 0 6px;
}
.gen-text {
  font-size: 15px;
  font-weight: 600;
  color: #303133;
  text-align: center;
}
.gen-elapsed {
  font-size: 13px;
  color: #606266;
}
.gen-tip {
  font-size: 12px;
  color: #909399;
  text-align: center;
  line-height: 1.7;
}
.fail-panel {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 14px;
  padding: 16px 0 6px;
}
.fail-text {
  font-size: 14px;
  color: #f56c6c;
  text-align: center;
  line-height: 1.7;
}
.fail-actions {
  display: flex;
  gap: 12px;
  margin-top: 4px;
}
</style>
