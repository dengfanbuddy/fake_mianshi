<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getTestDetail } from '../../api/written-test'

const route = useRoute()
const router = useRouter()
const sessionId = route.params.sessionId
const projectId = route.query.projectId

const loading = ref(false)
const detail = ref(null)
const activeNames = ref([])

const typeLabel = {
  SINGLE_CHOICE: '单选题',
  MULTIPLE_CHOICE: '多选题',
  FILL_BLANK: '填空题',
  SHORT_ANSWER: '简答题',
}

const totalScore = computed(() => detail.value?.totalScore ?? 0)
const results = computed(() => detail.value?.results || [])
const totalCount = computed(() => results.value.length)
const correctCount = computed(() => results.value.filter(r => r.isCorrect === true).length)
const scoreRate = computed(() =>
  totalCount.value ? Math.round((correctCount.value / totalCount.value) * 100) : 0
)

function formatAnswer(s) {
  if (s === undefined || s === null || s === '') return '未作答'
  // 多选答案如 "AB" → "A、B"
  if (typeof s === 'string' && /^[A-Za-z]+$/.test(s) && s.length > 1) {
    return s.split('').join('、')
  }
  return s
}

function fetchDetail() {
  loading.value = true
  getTestDetail(sessionId)
    .then(res => {
      if (res.code === 200) {
        detail.value = res.data
        // 默认全部展开
        activeNames.value = (res.data.results || []).map((_, i) => i)
      } else {
        ElMessage.error(res.message || '加载结果失败')
      }
    })
    .catch(() => {
      // request 拦截器已提示错误
    })
    .finally(() => {
      loading.value = false
    })
}

function goBackProject() {
  if (projectId) {
    router.push(`/project/${projectId}`)
  } else {
    ElMessage.info('项目详情页尚未实现，敬请期待')
  }
}

function goAnalysis() {
  router.push({ path: `/analysis/session/${sessionId}`, query: { projectId } })
}

onMounted(fetchDetail)
</script>

<template>
  <div class="result-page">
    <div class="result-card">
      <h2 class="result-title">笔试结果</h2>

      <div v-loading="loading" class="summary">
        <el-statistic title="总分" :value="totalScore" class="score-stat">
          <template #suffix><span class="score-unit">分</span></template>
        </el-statistic>
        <div class="summary-text">
          <div class="count">
            答对 <b>{{ correctCount }}</b> / {{ totalCount }} 题
          </div>
          <el-progress
            :percentage="scoreRate"
            :stroke-width="12"
            :color="scoreRate >= 60 ? '#67c23a' : scoreRate >= 40 ? '#e6a23c' : '#f56c6c'"
          />
        </div>
      </div>

      <el-collapse v-if="!loading && results.length" v-model="activeNames" class="result-collapse">
        <el-collapse-item v-for="(r, idx) in results" :key="r.questionId" :name="idx">
          <template #title>
            <div class="collapse-title">
              <span class="c-title">第 {{ idx + 1 }} 题</span>
              <span v-if="r.question" class="c-question">{{ r.question }}</span>
              <el-tag
                v-if="r.isCorrect === true"
                type="success"
                size="small"
                effect="dark"
                class="status-tag"
              >正确</el-tag>
              <el-tag
                v-else-if="r.isCorrect === false"
                type="danger"
                size="small"
                effect="dark"
                class="status-tag"
              >错误</el-tag>
              <el-tag
                v-else
                type="info"
                size="small"
                effect="plain"
                class="status-tag"
              >待AI分析</el-tag>
            </div>
          </template>

          <div class="collapse-body">
            <div v-if="r.type && typeLabel[r.type]" class="meta-row">
              <el-tag type="info" size="small" effect="plain">{{ typeLabel[r.type] }}</el-tag>
              <span class="score">得分：{{ r.score ?? 0 }} 分</span>
            </div>

            <div class="answer-row">
              <span class="label">你的答案：</span>
              <span :class="['value', { wrong: r.isCorrect === false }]">
                {{ formatAnswer(r.userAnswer) }}
              </span>
            </div>
            <div v-if="r.isCorrect !== true" class="answer-row">
              <span class="label">正确答案：</span>
              <span class="value correct">{{ formatAnswer(r.correctAnswer) }}</span>
            </div>
            <div v-if="r.isCorrect === null && r.type === 'SHORT_ANSWER'" class="answer-row ai-tip">
              简答题将由 AI 批改，点击下方「查看 AI 分析」获取详细评价。
            </div>
            <div v-if="r.explanation" class="explanation">
              <span class="label">解析：</span>
              <span class="value">{{ r.explanation }}</span>
            </div>
          </div>
        </el-collapse-item>
      </el-collapse>

      <el-empty v-if="!loading && !results.length" description="暂无题目结果" />

      <div class="actions">
        <el-button @click="goBackProject">返回项目</el-button>
        <el-button type="primary" @click="goAnalysis">查看 AI 分析</el-button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.result-page {
  min-height: 100vh;
  background: #f5f7fa;
  padding: 24px;
  box-sizing: border-box;
}
.result-card {
  max-width: 860px;
  margin: 0 auto;
  background: #fff;
  border-radius: 12px;
  padding: 32px;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.08);
}
.result-title {
  margin: 0 0 24px;
  font-size: 22px;
  text-align: center;
}
.summary {
  display: flex;
  align-items: center;
  gap: 40px;
  padding: 20px;
  background: #f9fafb;
  border-radius: 8px;
  margin-bottom: 24px;
}
.score-stat {
  flex-shrink: 0;
}
.score-unit {
  font-size: 14px;
  color: #909399;
  margin-left: 2px;
}
.summary-text {
  flex: 1;
  min-width: 0;
}
.count {
  font-size: 15px;
  color: #303133;
  margin-bottom: 12px;
}
.count b {
  font-size: 20px;
  color: #409eff;
}
.result-collapse {
  border-top: none;
}
.collapse-title {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}
.c-title {
  flex-shrink: 0;
  font-weight: 600;
}
.c-question {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: #606266;
  font-weight: normal;
}
.status-tag {
  flex-shrink: 0;
}
.collapse-body {
  padding: 4px 8px;
}
.meta-row {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 10px;
}
.score {
  font-size: 13px;
  color: #909399;
}
.answer-row {
  display: flex;
  align-items: baseline;
  margin-bottom: 8px;
  font-size: 14px;
  line-height: 1.6;
}
.answer-row .label {
  color: #909399;
  flex-shrink: 0;
  margin-right: 8px;
}
.answer-row .value {
  color: #303133;
  word-break: break-word;
}
.answer-row .value.wrong {
  color: #f56c6c;
}
.answer-row .value.correct {
  color: #67c23a;
}
.ai-tip {
  color: #e6a23c;
  font-size: 13px;
  margin-bottom: 8px;
}
.explanation {
  display: flex;
  font-size: 14px;
  line-height: 1.6;
  margin-top: 8px;
  padding: 10px 12px;
  background: #f4f4f5;
  border-radius: 6px;
}
.explanation .label {
  color: #909399;
  flex-shrink: 0;
  margin-right: 8px;
}
.explanation .value {
  color: #303133;
  word-break: break-word;
}
.actions {
  display: flex;
  justify-content: center;
  gap: 16px;
  margin-top: 28px;
}
</style>
