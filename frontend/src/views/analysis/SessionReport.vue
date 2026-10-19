<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getMockMessages, probeWrittenTestDetail } from '../../api/analysis'
import { getSessionInfo } from '../../api/session'
import { ArrowLeft, Refresh } from '@element-plus/icons-vue'
import { useSessionAnalysis } from '../../composables/useSessionAnalysis'
import ReportOverviewCard from '../../components/analysis/ReportOverviewCard.vue'
import HumorSummaryCard from '../../components/analysis/HumorSummaryCard.vue'
import StrengthWeaknessPanel from '../../components/analysis/StrengthWeaknessPanel.vue'
import PersonalityPanel from '../../components/analysis/PersonalityPanel.vue'
import KnowledgeGapPanel from '../../components/analysis/KnowledgeGapPanel.vue'
import CommunicationPanel from '../../components/analysis/CommunicationPanel.vue'
import ImprovementPlanPanel from '../../components/analysis/ImprovementPlanPanel.vue'
import QuestionAnalysisList from '../../components/analysis/QuestionAnalysisList.vue'
import GeneratingPanel from '../../components/analysis/GeneratingPanel.vue'
import GenerateTimeoutPanel from '../../components/analysis/GenerateTimeoutPanel.vue'
import InterviewReplayDialog from '../../components/analysis/InterviewReplayDialog.vue'

const route = useRoute()
const router = useRouter()
const sessionId = route.params.sessionId

// ---------- 会话类型判定 ----------
// 笔试会话有 written-test 记录；接口 404 说明是模拟面试
const sessionType = ref('') // 'written' | 'interview'
const written = ref(null)
const messages = ref([])

const loading = ref(true)
const messageLoading = ref(false)
const dialogVisible = ref(false)

// 单题折叠面板展开项
const activeNames = ref([])

// 分析报告的数据获取与生成状态机全部收敛在组合式函数里
const {
  analysis,
  generating,
  generateElapsed,
  generateTimedOut,
  stalled,
  streamText,
  streamReasoning,
  failReason,
  refreshing,
  generatingTitle,
  GENERATE_TIMEOUT,
  IDLE_TIMEOUT,
  fetchAnalysisOnce,
  startStreamingAnalysis,
  handleRefresh,
} = useSessionAnalysis(sessionId)

// ---------- 派生数据 ----------
const questionAnalyses = computed(() => analysis.value?.questionAnalyses || [])
const writtenResults = computed(() => written.value?.results || [])

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
const humorSummary = computed(() => analysis.value?.humorSummary || '')
const personalitySummary = computed(() => analysis.value?.personalitySummary || '')
const characterTraits = computed(() => analysis.value?.characterTraits || [])
const characterDefects = computed(() => analysis.value?.characterDefects || [])
const improvementPlan = computed(
  () => analysis.value?.improvementPlan || { topics: [], suggestions: [] }
)
const planTopics = computed(() => improvementPlan.value.topics || [])
const planSuggestions = computed(() => improvementPlan.value.suggestions || [])

// projectId：query 优先，其次 sessionStorage（笔试会话缓存），最后 localStorage 兜底
const projectId = ref(resolveProjectId())

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

  // 未带 projectId 时，从会话信息获取所属项目（保证返回能回到项目页）
  if (!projectId.value) {
    try {
      const sres = await getSessionInfo(sessionId)
      if (sres.code === 200 && sres.data?.projectId) {
        projectId.value = sres.data.projectId
      }
    } catch (e) {
      // 忽略，返回按钮会回首页
    }
  }

  // 判定会话类型：笔试有记录；模拟面试后端返回 404（静默探测，不弹错误提示）
  const wdata = await probeWrittenTestDetail(sessionId)
  if (wdata) {
    written.value = wdata
    sessionType.value = 'written'
  } else {
    sessionType.value = 'interview'
  }

  if (sessionType.value === 'interview') {
    await loadMessages()
  }
  // 笔试/面试：默认展开全部单题
  activeNames.value = mergedQuestions.value.map((_, i) => i)

  // 加载分析：未生成则打开 SSE 流式等待（实时展示 AI 生成过程）
  const ok = await fetchAnalysisOnce()
  if (!ok) {
    startStreamingAnalysis()
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
        <el-button
          v-if="!loading && (analysis || generateTimedOut)"
          class="refresh-btn"
          type="primary"
          plain
          size="small"
          :icon="Refresh"
          :loading="refreshing"
          @click="handleRefresh"
        >
          重新生成分析
        </el-button>
      </div>
    </header>

    <main class="report-body" v-loading="loading">
      <!-- 分析生成中：SSE 流式实时展示（直接显示原文） -->
      <GeneratingPanel
        v-if="generating"
        :title="generatingTitle" :elapsed="generateElapsed" :stalled="stalled"
        :stream-text="streamText" :stream-reasoning="streamReasoning" :idle-timeout="IDLE_TIMEOUT"
      />
      <!-- 生成超时/失败：提示 + 重试按钮 -->
      <GenerateTimeoutPanel
        v-else-if="generateTimedOut && !analysis"
        :fail-reason="failReason" :generate-timeout="GENERATE_TIMEOUT" @refresh="handleRefresh"
      />
      <template v-else-if="!loading && analysis">
        <!-- 1. 顶部概览卡 -->
        <ReportOverviewCard
          :analysis="analysis" :written="written" :session-type="sessionType"
          :session-id="sessionId" :message-count="messages.length"
          @view-dialog="dialogVisible = true"
        />

        <!-- 幽默总结卡 -->
        <HumorSummaryCard :humor-summary="humorSummary" />

        <!-- 2. 优势 / 弱项 -->
        <StrengthWeaknessPanel :strengths="strengths" :weaknesses="weaknesses" />

        <!-- 3. 性格总结 -->
        <PersonalityPanel
          :personality-summary="personalitySummary" :character-traits="characterTraits"
          :character-defects="characterDefects"
        />

        <!-- 4. 知识盲区 -->
        <KnowledgeGapPanel :knowledge-gaps="knowledgeGaps" />

        <!-- 5. 沟通表达（仅面试） -->
        <CommunicationPanel
          v-if="sessionType === 'interview'" :communication-evaluation="communicationEvaluation"
        />

        <!-- 6. 改进方案 -->
        <ImprovementPlanPanel :plan-topics="planTopics" :plan-suggestions="planSuggestions" />

        <!-- 7. 单题分析 + 8. 笔试补充 -->
        <QuestionAnalysisList v-model="activeNames" :questions="mergedQuestions" />
      </template>
    </main>

    <!-- 8. 面试回看弹窗 -->
    <InterviewReplayDialog v-model="dialogVisible" :messages="messages" :loading="messageLoading" />
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
.top-tag {
  display: flex;
  align-items: center;
  gap: 10px;
}
.refresh-btn {
  flex-shrink: 0;
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
</style>
