<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getHistoryAnalysis } from '../../api/analysis'
import { ArrowLeft } from '@element-plus/icons-vue'

const route = useRoute()
const router = useRouter()
const projectId = route.params.projectId

const loading = ref(true)
const history = ref(null) // 原始 { id, projectId, analysisData, generatedAt }
const report = ref(null) // 解析后的分析数据

// 项目名：query 优先，无则占位
const projectName = computed(
  () =>
    route.query.projectName ||
    route.query.title ||
    `项目 #${projectId}`
)

const TREND_META = {
  上升: { arrow: '↑', color: '#67c23a', label: '上升' },
  平稳: { arrow: '→', color: '#909399', label: '平稳' },
  下降: { arrow: '↓', color: '#f56c6c', label: '下降' },
}

const TREND_POS = {
  上升: 'calc(100% - 12px)',
  平稳: '50%',
  下降: '12px',
}

const trends = computed(() => (report.value?.trends || []).map((t) => {
  const meta = TREND_META[t.trend] || TREND_META.平稳
  return { ...t, ...meta, pos: TREND_POS[t.trend] || TREND_POS.平稳 }
}))

const recurringWeaknesses = computed(() => report.value?.recurringWeaknesses || [])
const recommendedFocus = computed(() => report.value?.recommendedFocus || [])
const overallProgress = computed(() => report.value?.overallProgress || '')

const upCount = computed(() => trends.value.filter((t) => t.trend === '上升').length)
const flatCount = computed(() => trends.value.filter((t) => t.trend === '平稳').length)
const downCount = computed(() => trends.value.filter((t) => t.trend === '下降').length)

const generatedAtText = computed(() => formatDateTime(history.value?.generatedAt))

// ---------- 解析 analysisData（后端返回 JSON 字符串） ----------
function parseAnalysisData(raw) {
  if (!raw) return {}
  if (typeof raw === 'object') return raw
  try {
    return JSON.parse(raw)
  } catch (e) {
    try {
      return JSON.parse(raw.replace(/```(json)?/g, '').trim())
    } catch (e2) {
      return {}
    }
  }
}

function formatDateTime(s) {
  if (!s) return ''
  const d = new Date(s)
  if (Number.isNaN(d.getTime())) return String(s)
  const p = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}`
}

async function fetchHistory() {
  loading.value = true
  try {
    const res = await getHistoryAnalysis(projectId)
    if (res.code === 200) {
      history.value = res.data
      report.value = parseAnalysisData(res.data?.analysisData)
    } else {
      ElMessage.error(res.message || '加载历史分析失败')
    }
  } catch (e) {
    // 拦截器已提示
  } finally {
    loading.value = false
  }
}

function goBack() {
  if (projectId) router.push(`/project/${projectId}`)
  else router.push('/')
}

onMounted(fetchHistory)
</script>

<template>
  <div class="history-page">
    <!-- 顶部导航 -->
    <header class="top-bar">
      <el-button class="back-btn" text :icon="ArrowLeft" @click="goBack">返回</el-button>
      <h1 class="top-title">历史综合分析报告</h1>
      <div class="top-tag">
        <el-tag type="success" effect="dark">综合分析</el-tag>
      </div>
    </header>

    <main class="history-body" v-loading="loading">
      <template v-if="!loading && report">
        <!-- 1. 整体评价卡 -->
        <section class="card progress-card">
          <div class="progress-icon">🎯</div>
          <div class="progress-content">
            <h3 class="card-title">整体进步评价</h3>
            <p v-if="overallProgress" class="progress-text">{{ overallProgress }}</p>
            <el-empty v-else description="暂无整体评价" :image-size="60" />
          </div>
        </section>

        <!-- 2. 趋势卡 -->
        <section v-if="trends.length" class="card">
          <div class="trend-header">
            <h3 class="card-title">📈 维度趋势</h3>
            <div class="trend-stats">
              <span class="stat-chip chip-up">↑ {{ upCount }}</span>
              <span class="stat-chip chip-flat">→ {{ flatCount }}</span>
              <span class="stat-chip chip-down">↓ {{ downCount }}</span>
            </div>
          </div>

          <div class="trend-list">
            <div v-for="(t, i) in trends" :key="i" class="trend-row">
              <div class="trend-top">
                <span class="trend-dim">{{ t.dimension }}</span>
                <el-tag size="small" :color="t.color" effect="dark" class="trend-tag">
                  {{ t.arrow }} {{ t.label }}
                </el-tag>
              </div>

              <!-- CSS 简易方向条：箭头位置表示走势 -->
              <div class="trend-track">
                <span class="trend-arrow" :style="{ left: t.pos, color: t.color }">{{ t.arrow }}</span>
              </div>

              <p v-if="t.detail" class="trend-detail">{{ t.detail }}</p>
            </div>
          </div>
        </section>

        <!-- 3. 反复弱点卡 -->
        <section v-if="recurringWeaknesses.length" class="card">
          <h3 class="card-title">🔁 反复出现的弱点</h3>
          <div class="tag-list">
            <el-tag v-for="(w, i) in recurringWeaknesses" :key="i" type="danger" class="item-tag" effect="light">
              {{ w }}
            </el-tag>
          </div>
        </section>

        <!-- 4. 下次重点卡 -->
        <section v-if="recommendedFocus.length" class="card">
          <h3 class="card-title">🎯 下次重点准备</h3>
          <el-timeline class="focus-timeline">
            <el-timeline-item
              v-for="(f, i) in recommendedFocus"
              :key="i"
              :type="i % 3 === 0 ? 'primary' : i % 3 === 1 ? 'warning' : 'success'"
              :hollow="true"
            >
              {{ f }}
            </el-timeline-item>
          </el-timeline>
        </section>

        <el-empty
          v-if="!trends.length && !recurringWeaknesses.length && !recommendedFocus.length && !overallProgress"
          description="暂无综合分析数据"
        />

        <!-- 生成时间 -->
        <p v-if="generatedAtText" class="generated-at">报告生成于 {{ generatedAtText }}</p>
      </template>

      <el-empty v-if="!loading && !report" description="未找到该项目的综合分析报告" />
    </main>
  </div>
</template>

<style scoped>
.history-page {
  min-height: 100vh;
  background: linear-gradient(180deg, #f1f8f2 0%, #f5f7fa 220px);
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
.history-body {
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
  color: #303133;
}

/* ---------- 整体评价 ---------- */
.progress-card {
  display: flex;
  gap: 18px;
  align-items: flex-start;
  background: linear-gradient(135deg, #f0f9eb 0%, #ffffff 65%);
}
.progress-icon {
  flex-shrink: 0;
  width: 56px;
  height: 56px;
  border-radius: 50%;
  background: #f0f9eb;
  border: 1px solid #c2e7b0;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 26px;
}
.progress-content {
  flex: 1;
  min-width: 0;
}
.progress-text {
  margin: 0;
  font-size: 14px;
  line-height: 1.9;
  color: #4a6b3a;
  white-space: pre-wrap;
}

/* ---------- 趋势 ---------- */
.trend-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}
.trend-header .card-title {
  margin-bottom: 0;
}
.trend-stats {
  display: flex;
  gap: 8px;
}
.stat-chip {
  font-size: 12px;
  font-weight: 600;
  padding: 2px 10px;
  border-radius: 999px;
}
.chip-up {
  background: #f0f9eb;
  color: #529b2e;
}
.chip-flat {
  background: #f4f4f5;
  color: #909399;
}
.chip-down {
  background: #fef0f0;
  color: #e05252;
}

.trend-list {
  margin-top: 18px;
  display: flex;
  flex-direction: column;
  gap: 18px;
}
.trend-row {
  padding: 12px 16px;
  background: #fafbfc;
  border-radius: 10px;
}
.trend-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
}
.trend-dim {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
}
.trend-tag {
  font-size: 12px;
}
.trend-track {
  position: relative;
  height: 6px;
  background: #ebeef5;
  border-radius: 999px;
  margin: 0 4px;
}
.trend-arrow {
  position: absolute;
  top: 50%;
  transform: translate(-50%, -50%);
  font-size: 16px;
  font-weight: 700;
  line-height: 1;
  text-shadow: 0 0 6px #fff;
}
.trend-detail {
  margin: 10px 0 0;
  font-size: 13px;
  line-height: 1.7;
  color: #909399;
  white-space: pre-wrap;
}

/* ---------- 弱点 / 重点 ---------- */
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
.focus-timeline {
  padding-left: 4px;
}

.generated-at {
  text-align: center;
  font-size: 12px;
  color: #c0c4cc;
  margin: 6px 0 0;
}
</style>
