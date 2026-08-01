<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  getSessionAnalysis,
  getWrittenTestDetail,
  getMockMessages,
} from '../../api/analysis'
import { WarningFilled, ChatDotRound, ArrowLeft } from '@element-plus/icons-vue'

const route = useRoute()
const router = useRouter()
const sessionId = route.params.sessionId

// ---------- 会话类型判定 ----------
// 笔试会话有 written-test 记录；接口 404 说明是模拟面试
const analysis = ref(null)
const sessionType = ref('') // 'written' | 'interview'
const written = ref(null)
const messages = ref([])

const loading = ref(true)
const messageLoading = ref(false)
const dialogVisible = ref(false)

// 单题折叠面板展开项
const activeNames = ref([])
// 回答全文展开状态：{ 索引: true }
const expandedAnswers = reactive({})

// projectId：query 优先，其次 sessionStorage（笔试会话缓存），最后 localStorage 兜底
const projectId = ref(resolveProjectId())

const MAX_PREVIEW = 200
const DIMENSIONS = [
  { key: 'accuracy', reasonKey: 'accuracyReason', label: '准确性' },
  { key: 'depth', reasonKey: 'depthReason', label: '深度' },
  { key: 'clarity', reasonKey: 'clarityReason', label: '清晰度' },
  { key: 'fluency', reasonKey: 'fluencyReason', label: '流畅度' },
]

// ---------- 派生数据 ----------
const displayScore = computed(() => {
  const s = analysis.value?.overallScore
  if (s !== null && s !== undefined && s !== '') return Number(s)
  const t = written.value?.totalScore
  if (t !== null && t !== undefined && t !== '') return Number(t)
  return null
})

const questionAnalyses = computed(() => analysis.value?.questionAnalyses || [])
const writtenResults = computed(() => written.value?.results || [])

// 该题有打分依据的维度列表
const dimsWithReasons = (qa) => DIMENSIONS.filter((d) => qa && qa[d.reasonKey])

// 单题分析 + 笔试原始记录按顺序对齐
const mergedQuestions = computed(() => {
  const a = questionAnalyses.value
  const w = writtenResults.value
  const len = Math.max(a.length, w.length)
  const list = []
  for (let i = 0; i < len; i++) {
    list.push({ index: i, analysis: a[i] || null, written: w[i] || null })
  }
  return list
})

const strengths = computed(() => analysis.value?.strengths || [])
const weaknesses = computed(() => analysis.value?.weaknesses || [])
const knowledgeGaps = computed(() => analysis.value?.knowledgeGaps || [])
const communicationEvaluation = computed(() => analysis.value?.communicationEvaluation || '')

// 新增字段：等级 / 薪资 / 性格
const overallLevel = computed(() => analysis.value?.overallLevel || '')
const expectedSalaryRange = computed(() => analysis.value?.expectedSalaryRange || '')
const personalitySummary = computed(() => analysis.value?.personalitySummary || '')
const characterTraits = computed(() => analysis.value?.characterTraits || [])
const characterDefects = computed(() => analysis.value?.characterDefects || [])

// 知识盲区：兼容对象 {point, explanation} 与旧版纯字符串
function gapPoint(g) {
  return typeof g === 'object' && g !== null ? g.point || '' : g || ''
}
function gapExplanation(g) {
  return typeof g === 'object' && g !== null ? g.explanation || '' : ''
}

const improvementPlan = computed(
  () => analysis.value?.improvementPlan || { topics: [], suggestions: [] }
)
const planTopics = computed(() => improvementPlan.value.topics || [])
const planSuggestions = computed(() => improvementPlan.value.suggestions || [])

// 改进方案 topics：兼容对象 {topic, action, example} 与旧版纯字符串
function planTopicText(t) {
  return typeof t === 'object' && t !== null ? t.topic || '' : t || ''
}
function planTopicAction(t) {
  return typeof t === 'object' && t !== null ? t.action || '' : ''
}
function planTopicExample(t) {
  return typeof t === 'object' && t !== null ? t.example || '' : ''
}

const totalCount = computed(() => writtenResults.value.length)
const correctCount = computed(() => writtenResults.value.filter((r) => r.isCorrect === true).length)

const scoreText = computed(() => {
  if (displayScore.value === null) return '—'
  if (displayScore.value >= 80) return '优秀'
  if (displayScore.value >= 60) return '良好'
  return '待加强'
})

// ---------- 颜色工具 ----------
function scoreColor(s) {
  if (s === null || s === undefined) return '#909399'
  if (s >= 80) return '#67c23a'
  if (s >= 60) return '#e6a23c'
  return '#f56c6c'
}

function dimColor(v) {
  const s = Number(v) || 0
  if (s >= 8) return '#67c23a'
  if (s >= 6) return '#e6a23c'
  return '#f56c6c'
}

// ---------- 文本工具 ----------
function fmt(value) {
  if (value === null || value === undefined || value === '') return '—'
  return String(value)
}

// 性格缺陷：兼容对象 {defect, improvement} 与纯字符串
function defectText(d) {
  return typeof d === 'object' && d !== null ? d.defect || '' : d || ''
}
function defectImprovement(d) {
  return typeof d === 'object' && d !== null ? d.improvement || '' : ''
}

// 难度标签颜色
function difficultyType(d) {
  const t = String(d || '')
  if (t.includes('初级')) return 'success'
  if (t.includes('高级')) return 'danger'
  return 'warning'
}

function formatDateTime(s) {
  if (!s) return ''
  const d = new Date(s)
  if (Number.isNaN(d.getTime())) return String(s)
  const p = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}`
}

function formatAnswer(s) {
  if (s === undefined || s === null || s === '') return '未作答'
  if (typeof s === 'string' && /^[A-Za-z]+$/.test(s) && s.length > 1) {
    return s.split('').join('、')
  }
  return s
}

function answerPreview(text) {
  const t = text || ''
  return t.length > MAX_PREVIEW ? `${t.slice(0, MAX_PREVIEW)}…` : t
}

function isExpanded(i) {
  return !!expandedAnswers[i]
}

function toggleExpand(i) {
  expandedAnswers[i] = !expandedAnswers[i]
}

// ---------- projectId 兜底 ----------
function resolveProjectId() {
  if (route.query.projectId) return route.query.projectId
  try {
    const raw = sessionStorage.getItem('written-test:session')
    if (raw) {
      const s = JSON.parse(raw)
      if (s?.projectId) return s.projectId
    }
  } catch (e) {
    // ignore
  }
  try {
    for (const key of ['projectId', 'currentProjectId']) {
      const v = localStorage.getItem(key)
      if (v) return v
    }
  } catch (e) {
    // ignore
  }
  return ''
}

// ---------- 数据加载 ----------
async function loadMessages() {
  messageLoading.value = true
  try {
    const res = await getMockMessages(sessionId)
    const list = res?.data || []
    messages.value = Array.isArray(list) ? list : []
  } catch (e) {
    // 拦截器已提示
  } finally {
    messageLoading.value = false
  }
}

async function fetchAll() {
  loading.value = true
  try {
    const res = await getSessionAnalysis(sessionId)
    if (res.code === 200) {
      analysis.value = res.data
    } else {
      ElMessage.error(res.message || '加载分析报告失败')
    }
  } catch (e) {
    // 拦截器已提示
  }

  // 判定会话类型：笔试有记录，模拟面试则 404
  let isWritten = false
  try {
    const wres = await getWrittenTestDetail(sessionId)
    if (wres.code === 200) {
      written.value = wres.data
      isWritten = true
    }
  } catch (e) {
    // 非笔试会话，属于正常分支
  }

  if (isWritten) {
    sessionType.value = 'written'
    // 笔试：默认展开全部单题
    activeNames.value = mergedQuestions.value.map((_, i) => i)
  } else {
    sessionType.value = 'interview'
    activeNames.value = mergedQuestions.value.map((_, i) => i)
    await loadMessages()
  }
  loading.value = false
}

function goBack() {
  if (projectId.value) router.push(`/project/${projectId.value}`)
  else router.push('/')
}

onMounted(fetchAll)
</script>

<template>
  <div class="report-page">
    <!-- 顶部导航 -->
    <header class="top-bar">
      <el-button class="back-btn" text :icon="ArrowLeft" @click="goBack">返回</el-button>
      <h1 class="top-title">AI 面试分析报告</h1>
      <div class="top-tag">
        <el-tag v-if="sessionType === 'written'" type="warning" effect="dark">笔试报告</el-tag>
        <el-tag v-else-if="sessionType === 'interview'" type="primary" effect="dark">模拟面试报告</el-tag>
        <el-tag v-else type="info" effect="plain">加载中</el-tag>
      </div>
    </header>

    <main class="report-body" v-loading="loading">
      <template v-if="!loading && analysis">
        <!-- 1. 顶部概览卡 -->
        <section class="card overview-card">
          <div class="overview-left">
            <el-progress
              type="circle"
              :percentage="Math.min(100, Math.max(0, displayScore ?? 0))"
              :width="132"
              :stroke-width="10"
              :color="scoreColor(displayScore)"
            >
              <template #default>
                <div class="circle-inner">
                  <span class="circle-score" :style="{ color: scoreColor(displayScore) }">
                    {{ displayScore ?? '—' }}
                  </span>
                  <span class="circle-unit">分</span>
                </div>
              </template>
            </el-progress>
          </div>

          <div class="overview-mid">
            <el-statistic title="综合评分" :value="displayScore ?? 0">
              <template #suffix><span class="stat-unit">分</span></template>
            </el-statistic>
            <div class="level-row">
              <span class="level-dot" :style="{ background: scoreColor(displayScore) }" />
              <span class="level-text" :style="{ color: scoreColor(displayScore) }">{{ scoreText }}</span>
            </div>
          </div>

          <div class="overview-meta">
            <div class="meta-item">
              <span class="meta-label">会话编号</span>
              <span class="meta-value">#{{ sessionId }}</span>
            </div>

            <div v-if="overallLevel" class="meta-item">
              <span class="meta-label">能力等级</span>
              <el-tag type="warning" size="small" effect="dark">{{ overallLevel }}</el-tag>
            </div>
            <div v-if="expectedSalaryRange" class="meta-item">
              <span class="meta-label">预期薪资</span>
              <span class="meta-value salary-text">{{ expectedSalaryRange }}</span>
            </div>

            <template v-if="sessionType === 'written' && totalCount">
              <div class="meta-item">
                <span class="meta-label">笔试得分</span>
                <span class="meta-value">{{ written?.totalScore ?? 0 }} 分</span>
              </div>
              <div class="meta-item">
                <span class="meta-label">答题情况</span>
                <span class="meta-value">
                  答对 <b class="ok">{{ correctCount }}</b> / {{ totalCount }} 题
                </span>
              </div>
            </template>

            <template v-else-if="sessionType === 'interview'">
              <div class="meta-item">
                <span class="meta-label">对话轮次</span>
                <span class="meta-value">{{ messages.length }} 条消息</span>
              </div>
              <div class="meta-item">
                <el-button type="primary" plain size="small" :icon="ChatDotRound" @click="dialogVisible = true">
                  查看完整对话
                </el-button>
              </div>
            </template>
          </div>
        </section>

        <!-- 2. 优势 / 弱项 -->
        <section v-if="strengths.length || weaknesses.length" class="grid-2">
          <div class="card">
            <h3 class="card-title good-title">✅ 优势亮点</h3>
            <div v-if="strengths.length" class="tag-list">
              <el-tag v-for="(s, i) in strengths" :key="i" type="success" class="item-tag" effect="light">
                {{ s }}
              </el-tag>
            </div>
            <el-empty v-else description="暂无优势分析" :image-size="60" />
          </div>
          <div class="card">
            <h3 class="card-title bad-title">⚠️ 待改进</h3>
            <div v-if="weaknesses.length" class="tag-list">
              <el-tag v-for="(w, i) in weaknesses" :key="i" type="danger" class="item-tag" effect="light">
                {{ w }}
              </el-tag>
            </div>
            <el-empty v-else description="暂无弱项分析" :image-size="60" />
          </div>
        </section>

        <!-- 3. 性格总结 -->
        <section
          v-if="personalitySummary || characterTraits.length || characterDefects.length"
          class="card"
        >
          <h3 class="card-title info-title">🧠 性格与特质总结</h3>
          <p v-if="personalitySummary" class="para-text personality-text">{{ personalitySummary }}</p>
          <div v-if="characterTraits.length" class="tag-list traits-tags">
            <el-tag v-for="(t, i) in characterTraits" :key="i" type="primary" effect="plain" class="item-tag">
              {{ t }}
            </el-tag>
          </div>
          <div v-if="characterDefects.length" class="defect-list">
            <div v-for="(d, i) in characterDefects" :key="i" class="defect-item">
              <div class="defect-head">
                <span class="defect-badge">缺陷 {{ i + 1 }}</span>
                <span class="defect-name">{{ defectText(d) }}</span>
              </div>
              <p v-if="defectImprovement(d)" class="defect-fix">💡 改进：{{ defectImprovement(d) }}</p>
            </div>
          </div>
        </section>

        <!-- 4. 知识盲区 -->
        <section v-if="knowledgeGaps.length" class="card">
          <h3 class="card-title warn-title">
            <el-icon class="title-icon" color="#e6a23c"><WarningFilled /></el-icon>
            知识盲区
          </h3>
          <ul class="gap-list">
            <li v-for="(g, i) in knowledgeGaps" :key="i" class="gap-item">
              <el-icon class="gap-icon" color="#e6a23c"><WarningFilled /></el-icon>
              <div class="gap-content">
                <div class="gap-point">{{ gapPoint(g) }}</div>
                <div v-if="gapExplanation(g)" class="gap-explain">
                  <span class="gap-explain-label">核心答案要点</span>
                  <span class="gap-explain-text">{{ gapExplanation(g) }}</span>
                </div>
              </div>
            </li>
          </ul>
        </section>

        <!-- 5. 沟通表达（仅面试） -->
        <section v-if="sessionType === 'interview' && communicationEvaluation" class="card">
          <h3 class="card-title info-title">💬 沟通表达评价</h3>
          <p class="para-text">{{ communicationEvaluation }}</p>
        </section>

        <!-- 6. 改进方案 -->
        <section v-if="planTopics.length || planSuggestions.length" class="card">
          <h3 class="card-title">🚀 改进方案</h3>
          <template v-if="planTopics.length">
            <h4 class="sub-title">学习方向</h4>
            <el-timeline class="plan-timeline">
              <el-timeline-item
                v-for="(t, i) in planTopics"
                :key="i"
                :type="i % 3 === 0 ? 'primary' : i % 3 === 1 ? 'success' : 'warning'"
                :hollow="true"
              >
                <div class="plan-item">
                  <div class="plan-topic">{{ planTopicText(t) }}</div>
                  <div v-if="planTopicAction(t)" class="plan-action">做法：{{ planTopicAction(t) }}</div>
                  <div v-if="planTopicExample(t)" class="plan-example">实例：{{ planTopicExample(t) }}</div>
                </div>
              </el-timeline-item>
            </el-timeline>
          </template>
          <template v-if="planSuggestions.length">
            <h4 class="sub-title">具体建议</h4>
            <ul class="suggest-list">
              <li v-for="(s, i) in planSuggestions" :key="i" class="suggest-item">
                <span class="suggest-num">{{ i + 1 }}</span>
                <span>{{ s }}</span>
              </li>
            </ul>
          </template>
        </section>

        <!-- 7. 单题分析 + 8. 笔试补充 -->
        <section v-if="mergedQuestions.length" class="card">
          <h3 class="card-title">📝 逐题深度分析</h3>
          <el-collapse v-model="activeNames" class="qa-collapse">
            <el-collapse-item v-for="q in mergedQuestions" :key="q.index" :name="q.index">
              <template #title>
                <div class="qa-title">
                  <span class="qa-no">第 {{ q.index + 1 }} 题</span>
                  <el-tag v-if="q.written?.isCorrect === true" type="success" size="small" effect="dark">正确</el-tag>
                  <el-tag v-else-if="q.written?.isCorrect === false" type="danger" size="small" effect="dark">错误</el-tag>
                  <el-tag v-else-if="q.analysis" type="info" size="small" effect="plain">已分析</el-tag>
                  <el-tooltip
                    v-if="q.analysis?.questionContent"
                    :content="q.analysis.questionContent"
                    placement="top"
                    :show-after="300"
                  >
                    <span class="qa-brief">{{ q.analysis.questionContent }}</span>
                  </el-tooltip>
                  <span v-else-if="q.written" class="qa-brief qa-brief-empty">（题目内容见笔试记录）</span>
                </div>
              </template>

              <div class="qa-body">
                <!-- 题干 + 分类标签 -->
                <div v-if="q.analysis?.questionContent" class="qa-block">
                  <span class="qa-label">题干</span>
                  <p class="qa-text">{{ q.analysis.questionContent }}</p>
                  <div v-if="q.analysis.category || q.analysis.difficulty || q.analysis.focusPoint" class="qa-tags">
                    <el-tag v-if="q.analysis.category" size="small" type="primary" effect="plain">{{ q.analysis.category }}</el-tag>
                    <el-tag v-if="q.analysis.difficulty" size="small" :type="difficultyType(q.analysis.difficulty)" effect="plain">{{ q.analysis.difficulty }}</el-tag>
                    <el-tag v-if="q.analysis.focusPoint" size="small" type="info" effect="plain">考察：{{ q.analysis.focusPoint }}</el-tag>
                  </div>
                </div>

                <!-- 你的回答 -->
                <div v-if="q.analysis?.answerContent" class="qa-block">
                  <span class="qa-label">你的回答</span>
                  <p class="qa-text">{{ isExpanded(q.index) ? q.analysis.answerContent : answerPreview(q.analysis.answerContent) }}</p>
                  <el-button
                    v-if="(q.analysis.answerContent || '').length > MAX_PREVIEW"
                    link
                    type="primary"
                    size="small"
                    class="expand-btn"
                    @click="toggleExpand(q.index)"
                  >
                    {{ isExpanded(q.index) ? '收起' : '展开全文' }}
                  </el-button>
                </div>

                <!-- 各维度评分（附打分依据） -->
                <div v-if="q.analysis" class="dims">
                  <div v-for="d in DIMENSIONS" :key="d.key" class="dim-row">
                    <span class="dim-name">{{ d.label }}</span>
                    <el-progress
                      class="dim-bar"
                      :percentage="Math.round((Number(q.analysis[d.key]) || 0) * 10)"
                      :stroke-width="8"
                      :color="dimColor(q.analysis[d.key])"
                      :show-text="false"
                    />
                    <span class="dim-score" :style="{ color: dimColor(q.analysis[d.key]) }">
                      {{ Number(q.analysis[d.key]) || 0 }}/10
                    </span>
                  </div>
                  <div v-if="dimsWithReasons(q.analysis).length" class="dim-reasons">
                    <div v-for="d in dimsWithReasons(q.analysis)" :key="d.key" class="dim-reason">
                      <span class="dim-reason-label">{{ d.label }}：</span>
                      <span class="dim-reason-text">{{ q.analysis[d.reasonKey] }}</span>
                    </div>
                  </div>
                </div>

                <!-- 回答思路 -->
                <div v-if="q.analysis?.answerApproach" class="qa-block">
                  <span class="qa-label">这类题的回答思路</span>
                  <p class="qa-text approach-text">{{ q.analysis.answerApproach }}</p>
                </div>

                <!-- 优秀回答示例 -->
                <div v-if="q.analysis?.example" class="qa-block">
                  <span class="qa-label">优秀回答示例</span>
                  <p class="qa-text example-text">{{ q.analysis.example }}</p>
                </div>

                <!-- 语气 / 改进建议 -->
                <div v-if="q.analysis?.toneEvaluation" class="qa-block">
                  <span class="qa-label">语气评价</span>
                  <p class="qa-text">{{ q.analysis.toneEvaluation }}</p>
                </div>
                <div v-if="q.analysis?.improvementSuggestion" class="qa-block">
                  <span class="qa-label">改进建议</span>
                  <p class="qa-text suggest-text">{{ q.analysis.improvementSuggestion }}</p>
                </div>

                <!-- 笔试原始记录 -->
                <template v-if="q.written">
                  <el-divider class="written-divider">笔试记录</el-divider>
                  <div v-if="q.written.type" class="written-meta">
                    <el-tag size="small" type="info" effect="plain">{{ q.written.type }}</el-tag>
                    <span class="written-score">得分：{{ q.written.score ?? 0 }} 分</span>
                  </div>
                  <div class="answer-row">
                    <span class="qa-label">你的答案</span>
                    <span :class="['answer-val', { wrong: q.written.isCorrect === false }]">
                      {{ formatAnswer(q.written.userAnswer) }}
                    </span>
                  </div>
                  <div v-if="q.written.isCorrect !== true" class="answer-row">
                    <span class="qa-label">正确答案</span>
                    <span class="answer-val correct">{{ formatAnswer(q.written.correctAnswer) }}</span>
                  </div>
                  <div v-if="q.written.explanation" class="qa-block">
                    <span class="qa-label">解析</span>
                    <p class="qa-text explain-text">{{ q.written.explanation }}</p>
                  </div>
                </template>
              </div>
            </el-collapse-item>
          </el-collapse>
        </section>

        <el-empty v-if="!mergedQuestions.length" description="暂无逐题分析" />
      </template>
    </main>

    <!-- 8. 面试回看弹窗 -->
    <el-dialog v-model="dialogVisible" title="完整对话回看" width="680px" top="6vh">
      <div class="replay-box" v-loading="messageLoading">
        <template v-if="!messageLoading">
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
  </div>
</template>

<style scoped>
.report-page {
  min-height: 100vh;
  background: linear-gradient(180deg, #f0f4fb 0%, #f5f7fa 220px);
  padding-bottom: 48px;
}

/* ---------- 顶部导航 ---------- */
.top-bar {
  position: sticky;
  top: 0;
  z-index: 10;
  display: flex;
  align-items: center;
  justify-content: space-between;
  max-width: 920px;
  margin: 0 auto;
  padding: 16px 8px 12px;
  background: rgba(245, 247, 250, 0.92);
  backdrop-filter: blur(6px);
}
.top-title {
  margin: 0;
  font-size: 20px;
  font-weight: 700;
  color: #303133;
}
.back-btn {
  min-width: 64px;
}

/* ---------- 内容 ---------- */
.report-body {
  max-width: 920px;
  margin: 0 auto;
  padding: 0 8px;
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.card {
  background: #fff;
  border-radius: 14px;
  padding: 24px 26px;
  box-shadow: 0 2px 12px rgba(31, 45, 61, 0.06);
  border: 1px solid #ebeef5;
}

.card-title {
  margin: 0 0 16px;
  font-size: 16px;
  font-weight: 700;
  display: flex;
  align-items: center;
  gap: 6px;
}
.card-title.good-title {
  color: #529b2e;
}
.card-title.bad-title {
  color: #e05252;
}
.card-title.warn-title {
  color: #b88230;
}
.card-title.info-title {
  color: #337ecc;
}
.title-icon {
  font-size: 16px;
}

/* ---------- 概览卡 ---------- */
.overview-card {
  display: flex;
  align-items: center;
  gap: 32px;
  padding: 28px 32px;
}
.overview-left {
  flex-shrink: 0;
}
.circle-inner {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
}
.circle-score {
  font-size: 30px;
  font-weight: 800;
  line-height: 1;
}
.circle-unit {
  font-size: 12px;
  color: #909399;
  margin-top: 2px;
}
.overview-mid {
  flex: 1;
  min-width: 120px;
}
.stat-unit {
  font-size: 13px;
  color: #909399;
}
.level-row {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 8px;
}
.level-dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
}
.level-text {
  font-size: 14px;
  font-weight: 600;
}
.overview-meta {
  display: flex;
  flex-direction: column;
  gap: 10px;
  border-left: 1px solid #f0f0f0;
  padding-left: 28px;
}
.meta-item {
  display: flex;
  align-items: center;
  gap: 8px;
}
.meta-label {
  font-size: 13px;
  color: #909399;
}
.meta-value {
  font-size: 14px;
  color: #303133;
  font-weight: 500;
}
.meta-value .ok {
  color: #67c23a;
  font-size: 16px;
}
.salary-text {
  color: #e6a23c;
  font-weight: 700;
}

/* ---------- 性格总结 ---------- */
.personality-text {
  background: #f0f9eb;
  color: #529b2e;
}
.traits-tags {
  margin-top: 12px;
}
.defect-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-top: 12px;
}
.defect-item {
  padding: 10px 14px;
  background: #fef0f0;
  border-radius: 8px;
  font-size: 14px;
  color: #d35050;
  line-height: 1.6;
}
.defect-head {
  display: flex;
  align-items: center;
  gap: 8px;
}
.defect-badge {
  flex-shrink: 0;
  font-size: 11px;
  background: #f56c6c;
  color: #fff;
  border-radius: 4px;
  padding: 1px 8px;
}
.defect-name {
  font-weight: 600;
}
.defect-fix {
  margin: 6px 0 0;
  color: #b88230;
  font-size: 13px;
}

/* ---------- 知识盲区 ---------- */
.gap-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.gap-item {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  padding: 10px 14px;
  background: #fdf6ec;
  border-radius: 8px;
  font-size: 14px;
  color: #8a6d3b;
  line-height: 1.6;
}
.gap-icon {
  margin-top: 3px;
  flex-shrink: 0;
}
.gap-content {
  min-width: 0;
  flex: 1;
}
.gap-point {
  font-weight: 600;
  color: #7d5a24;
}
.gap-explain {
  margin-top: 6px;
  padding: 8px 10px;
  background: #fff;
  border-radius: 6px;
  font-size: 13px;
  color: #5c4a2b;
}
.gap-explain-label {
  display: inline-block;
  font-size: 11px;
  color: #e6a23c;
  background: #fdf0dc;
  border-radius: 4px;
  padding: 0 6px;
  margin-right: 6px;
  margin-bottom: 4px;
}
.gap-explain-text {
  white-space: pre-wrap;
  word-break: break-word;
}

/* ---------- 双列 ---------- */
.grid-2 {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 18px;
}
@media (max-width: 720px) {
  .grid-2 {
    grid-template-columns: 1fr;
  }
  .overview-card {
    flex-direction: column;
    align-items: flex-start;
    gap: 20px;
  }
  .overview-meta {
    border-left: none;
    border-top: 1px solid #f0f0f0;
    padding-left: 0;
    padding-top: 16px;
  }
}
.tag-list {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}
.item-tag {
  font-size: 13px;
  line-height: 26px;
  height: auto;
  white-space: normal;
  padding: 2px 12px;
}

/* ---------- 知识盲区 ---------- */
.gap-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.gap-item {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  padding: 10px 14px;
  background: #fdf6ec;
  border-radius: 8px;
  font-size: 14px;
  color: #8a6d3b;
  line-height: 1.6;
}
.gap-icon {
  margin-top: 3px;
  flex-shrink: 0;
}

/* ---------- 沟通表达 ---------- */
.para-text {
  margin: 0;
  padding: 14px 16px;
  background: #ecf5ff;
  border-radius: 8px;
  font-size: 14px;
  line-height: 1.8;
  color: #3a6ea5;
  white-space: pre-wrap;
}

/* ---------- 改进方案 ---------- */
.sub-title {
  margin: 4px 0 10px;
  font-size: 14px;
  color: #909399;
  font-weight: 600;
}
.plan-timeline {
  padding-left: 4px;
}
.plan-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.plan-topic {
  font-weight: 600;
  color: #303133;
}
.plan-action {
  font-size: 13px;
  color: #606266;
  line-height: 1.6;
}
.plan-example {
  font-size: 13px;
  color: #b88230;
  line-height: 1.6;
  background: #fdf6ec;
  border-radius: 6px;
  padding: 6px 10px;
}
.suggest-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.suggest-item {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  font-size: 14px;
  line-height: 1.7;
  color: #303133;
}
.suggest-num {
  flex-shrink: 0;
  width: 22px;
  height: 22px;
  border-radius: 50%;
  background: #409eff;
  color: #fff;
  font-size: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-top: 2px;
}

/* ---------- 逐题分析 ---------- */
.qa-collapse {
  border-top: none;
}
.qa-collapse :deep(.el-collapse-item__header) {
  padding-left: 8px;
}
.qa-collapse :deep(.el-collapse-item__content) {
  padding: 0 8px 8px;
}
.qa-title {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
  flex: 1;
  overflow: hidden;
}
.qa-no {
  flex-shrink: 0;
  font-weight: 600;
}
.qa-brief {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: #909399;
  font-weight: normal;
  min-width: 0;
  flex: 1;
}
.qa-brief-empty {
  color: #c0c4cc;
  font-style: italic;
}
.qa-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 8px;
}
.approach-text {
  background: #ecf5ff;
  border-radius: 6px;
  padding: 8px 12px;
  color: #3a6ea5;
}
.example-text {
  background: #f0f9eb;
  border-radius: 6px;
  padding: 8px 12px;
  color: #529b2e;
}
.qa-body {
  padding: 8px 10px 10px;
}
.qa-block {
  margin-bottom: 12px;
}
.qa-label {
  display: inline-block;
  font-size: 12px;
  color: #909399;
  margin-bottom: 4px;
  background: #f4f4f5;
  padding: 1px 8px;
  border-radius: 4px;
}
.qa-text {
  margin: 0;
  font-size: 14px;
  line-height: 1.75;
  color: #303133;
  white-space: pre-wrap;
  word-break: break-word;
}
.suggest-text {
  color: #b88230;
}
.explain-text {
  background: #f4f4f5;
  padding: 10px 12px;
  border-radius: 6px;
}
.expand-btn {
  margin-top: 4px;
}

.dims {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-bottom: 12px;
  padding: 12px 14px;
  background: #f9fafb;
  border-radius: 8px;
}
.dim-row {
  display: flex;
  align-items: center;
  gap: 10px;
}
.dim-name {
  width: 52px;
  flex-shrink: 0;
  font-size: 13px;
  color: #606266;
}
.dim-bar {
  flex: 1;
  min-width: 0;
}
.dim-score {
  width: 46px;
  flex-shrink: 0;
  text-align: right;
  font-size: 12px;
  font-weight: 600;
}
.dim-reasons {
  margin-top: 6px;
  border-top: 1px dashed #e4e7ed;
  padding-top: 8px;
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.dim-reason {
  font-size: 13px;
  line-height: 1.6;
  color: #606266;
}
.dim-reason-label {
  font-weight: 600;
  color: #409eff;
  flex-shrink: 0;
}
.dim-reason-text {
  white-space: pre-wrap;
  word-break: break-word;
}

.written-divider {
  margin: 12px 0;
  color: #909399;
}
.written-meta {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 10px;
}
.written-score {
  font-size: 13px;
  color: #909399;
}
.answer-row {
  display: flex;
  align-items: baseline;
  gap: 8px;
  margin-bottom: 10px;
  font-size: 14px;
}
.answer-val {
  color: #303133;
  word-break: break-word;
}
.answer-val.wrong {
  color: #f56c6c;
}
.answer-val.correct {
  color: #67c23a;
}

/* ---------- 面试回看 ---------- */
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
.bub-interviewer .replay-meta {
  color: rgba(255, 255, 255, 0.6);
}
.bub-candidate .replay-meta {
  color: #a0a6ad;
}
</style>
