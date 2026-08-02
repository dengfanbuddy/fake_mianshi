<script setup>
import { ref, computed, onBeforeUnmount } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { WarningFilled } from '@element-plus/icons-vue'
import { useWrittenTestStore } from '../../stores/writtenTest'

const route = useRoute()
const router = useRouter()
const store = useWrittenTestStore()
const projectId = route.params.projectId
// 完整流程标记：笔试完成后自动进入模拟面试（带 refWritten 参考笔试结果）
const flowFull = route.query.flow === 'full'

const timeLimit = ref(60)
const questionCount = ref(12)

// 出题等待状态
const WAIT_TIMEOUT = 300 // 总等待上限（秒）
const IDLE_TIMEOUT = 60 // 流式空闲超时（秒，无新内容则判定中断）
const waiting = ref(false)
const elapsed = ref(0)
const idleElapsed = ref(0)
const streamText = ref('') // 流式原文（JSON）
const streamReasoning = ref('') // AI 思考过程（思考中提示）
const genTitle = computed(() => {
  if (streamText.value) return 'AI 正在生成题目，边写边展示…'
  if (streamReasoning.value) return 'AI 正在思考，即将开始输出…'
  return 'AI 正在准备题目…'
})
const failed = ref(false)
const failReason = ref('')

let timer = null
let idleTimer = null
let controller = null
let finished = false // 已成功完成（避免超时计时器误触发失败）

function resetTimers() {
  if (timer) clearInterval(timer)
  if (idleTimer) clearInterval(idleTimer)
  timer = null
  idleTimer = null
}

function startTimers() {
  elapsed.value = 0
  idleElapsed.value = 0
  timer = setInterval(() => {
    elapsed.value += 1
    if (elapsed.value >= WAIT_TIMEOUT && waiting.value && !finished) {
      abortByTimeout('AI 出题超过 ' + WAIT_TIMEOUT + ' 秒无响应，请重试')
    }
  }, 1000)
  idleTimer = setInterval(() => {
    idleElapsed.value += 1
    if (idleElapsed.value >= IDLE_TIMEOUT && waiting.value && !finished && (streamText.value || streamReasoning.value)) {
      abortByTimeout('AI 出题中断（' + IDLE_TIMEOUT + ' 秒无新内容），请重试')
    }
  }, 1000)
  // 8 秒无任何事件 → 提示连接异常（等待总超时兜底）
  setTimeout(() => {
    if (waiting.value && !finished && !streamText.value && !streamReasoning.value) {
      streamText.value = '⚠️ AI 连接似乎无响应（已等待 8 秒无内容），仍在尝试，若长时间无反应可点击重新尝试。'
    }
  }, 8000)
}

function fail(message) {
  resetTimers()
  waiting.value = false
  failed.value = true
  failReason.value = message
}

function abortByTimeout(message) {
  if (controller) controller.abort()
  fail(message)
}

/** 解析一条 SSE 事件（event: xxx / data: xxx），返回 { event, data } */
function parseSseEvent(raw) {
  let event = 'message'
  const dataLines = []
  for (const line of raw.split('\n')) {
    if (line.startsWith('event:')) event = line.slice(6).trim()
    else if (line.startsWith('data:')) dataLines.push(line.slice(5).trim())
  }
  return { event, data: dataLines.join('\n') }
}

async function handleStart() {
  waiting.value = true
  failed.value = false
  failReason.value = ''
  finished.value = false
  streamText.value = ''
  streamReasoning.value = ''
  controller = new AbortController()
  startTimers()
  try {
    const resp = await fetch(`/api/written-test/stream-start/${projectId}`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ timeLimit: timeLimit.value, questionCount: questionCount.value }),
      signal: controller.signal,
    })
    if (!resp.ok || !resp.body) {
      throw new Error('HTTP ' + resp.status)
    }
    const reader = resp.body.getReader()
    const decoder = new TextDecoder()
    let buffer = ''
    while (true) {
      const { done, value } = await reader.read()
      if (done) break
      buffer += decoder.decode(value, { stream: true })
      let idx
      while ((idx = buffer.indexOf('\n\n')) >= 0) {
        const raw = buffer.slice(0, idx)
        buffer = buffer.slice(idx + 2)
        const { event, data } = parseSseEvent(raw)
        if (event === 'delta' && data) {
          streamText.value += data
          idleElapsed.value = 0 // 有内容则重置空闲计时
        } else if (event === 'reasoning' && data) {
          streamReasoning.value += data
          idleElapsed.value = 0 // 思考过程也算活跃，避免误判中断
        } else if (event === 'done' && data) {
          finished.value = true
          handleDone(data)
        } else if (event === 'error' && data) {
          fail(data.replace(/^AI 出题失败：/, ''))
        }
      }
    }
    // 流正常结束但未收到 done（异常截断）
    if (waiting.value && !finished.value && !failed.value) {
      fail('AI 出题连接中断，请重试')
    }
  } catch (e) {
    if (e?.name === 'AbortError' || e?.message?.includes('abort')) {
      // 超时中止：fail 已由 abortByTimeout 触发
    } else if (!finished.value) {
      fail('AI 出题失败：' + (e?.message || '网络异常') + '，请重试')
    }
  }
}

function handleDone(data) {
  resetTimers()
  let res
  try {
    res = JSON.parse(data)
  } catch (e) {
    fail('出题完成但返回数据异常，请重试')
    return
  }
  if (res?.sessionId) {
    store.save({
      sessionId: res.sessionId,
      projectId,
      timeLimit: res.timeLimit,
      deadline: Date.now() + res.timeLimit * 60000,
      questions: res.questions || [],
      flow: flowFull ? 'full' : null,
    })
    waiting.value = false
    ElMessage.success('考试开始，祝你好运！')
    router.push(`/written-test/exam/${res.sessionId}`)
  } else {
    fail('出题完成但缺少考试信息，请重试')
  }
}

function goBack() {
  router.back()
}

onBeforeUnmount(() => {
  resetTimers()
  if (controller) controller.abort()
})
</script>

<template>
  <div class="start-page">
    <div class="start-card wide">
      <h2 class="title">AI 笔试考试</h2>
      <p class="subtitle">项目 ID：<el-tag type="info">{{ projectId }}</el-tag></p>

      <!-- 等待 AI 出题：实时流式展示 -->
      <div v-if="waiting && !failed" class="generating-panel">
        <div class="gen-header">
          <el-progress
            :percentage="Math.min(100, Math.round((elapsed / WAIT_TIMEOUT) * 100))"
            :stroke-width="6"
            :show-text="false"
            class="gen-progress"
          />
          <div class="gen-meta">
            <span class="gen-text">{{ genTitle }}</span>
            <span class="gen-elapsed">已等待 {{ elapsed }} 秒</span>
          </div>
        </div>
        <div class="gen-stream raw-text">
          <template v-if="streamText">{{ streamText }}</template>
          <template v-else-if="streamReasoning">
            <div class="reasoning-box">
              <div class="thinking-hint">🤔 AI 正在思考中（已思考 {{ streamReasoning.length }} 字）…</div>
              <pre class="thinking-text">{{ streamReasoning }}</pre>
            </div>
          </template>
          <template v-else>正在连接 AI，开始生成题目…</template>
        </div>
        <div class="gen-tip">正在实时预览 AI 生成的题目，完成后自动进入考试；若中断会提示重试。</div>
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

      <!-- 配置表单 -->
      <template v-else>
        <el-form label-width="100px" label-position="left" class="config-form">
          <el-form-item label="考试时长">
            <el-input-number v-model="timeLimit" :min="10" :max="180" :step="5" />
            <span class="unit">分钟</span>
          </el-form-item>
          <el-form-item label="题目数量">
            <el-input-number v-model="questionCount" :min="5" :max="30" :step="1" />
            <span class="unit">道</span>
          </el-form-item>
        </el-form>

        <p class="tip">包含单选题、多选题、填空题与简答题。点击开始后 AI 会实时生成题目，无需干等。</p>

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
.start-card.wide {
  width: 720px;
  max-width: 94vw;
}
.title {
  margin: 0 0 8px;
  font-size: 22px;
  text-align: center;
}
.subtitle {
  margin: 0 0 20px;
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
  gap: 12px;
}
.gen-header {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.gen-progress {
  width: 100%;
}
.gen-meta {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.gen-text {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
}
.gen-elapsed {
  font-size: 12px;
  color: #909399;
}
.gen-stream {
  max-height: 46vh;
  overflow-y: auto;
  background: #fafbfc;
  border: 1px solid #ebeef5;
  border-radius: 10px;
  padding: 14px 18px;
  font-size: 13px;
  line-height: 1.8;
  color: #303133;
}
.gen-stream.raw-text {
  white-space: pre-wrap;
  word-break: break-all;
  overflow-wrap: anywhere;
}
.reasoning-box {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.gen-stream .thinking-hint {
  font-size: 13px;
  color: #e6a23c;
  font-weight: 600;
}
.gen-stream .thinking-text {
  font-size: 12px;
  color: #909399;
  white-space: pre-wrap;
  word-break: break-word;
  font-family: inherit;
  margin-top: 6px;
}
.gen-tip {
  font-size: 12px;
  color: #909399;
  text-align: center;
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
/* 流式 markdown 内容样式 */
.markdown-body h3 {
  font-size: 14px;
  margin: 10px 0 4px;
  color: #303133;
}
.markdown-body ul {
  margin: 0;
  padding-left: 18px;
}
.markdown-body li {
  margin-bottom: 2px;
}
.markdown-body p {
  margin: 4px 0;
}
</style>
