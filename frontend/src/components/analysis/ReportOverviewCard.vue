<script setup>
import { computed } from 'vue'
import { ChatDotRound } from '@element-plus/icons-vue'

// 顶部概览卡：总分 + 等级 + 会话元信息（纯展示，数据由父组件传入）
const props = defineProps({
  // 分析报告 DTO
  analysis: { type: Object, default: null },
  // 笔试原始记录（面试会话为 null）
  written: { type: Object, default: null },
  // 'written' | 'interview'
  sessionType: { type: String, default: '' },
  // 会话 ID
  sessionId: { type: [String, Number], default: '' },
  // 面试消息条数
  messageCount: { type: Number, default: 0 },
})

const emit = defineEmits(['view-dialog'])

const writtenResults = computed(() => props.written?.results || [])
const totalCount = computed(() => writtenResults.value.length)
const correctCount = computed(() => writtenResults.value.filter((r) => r.isCorrect === true).length)

const displayScore = computed(() => {
  const s = props.analysis?.overallScore
  if (s !== null && s !== undefined && s !== '') return Number(s)
  const t = props.written?.totalScore
  if (t !== null && t !== undefined && t !== '') return Number(t)
  return null
})

const scoreText = computed(() => {
  if (displayScore.value === null) return '—'
  if (displayScore.value >= 80) return '优秀'
  if (displayScore.value >= 60) return '良好'
  return '待加强'
})

const overallLevel = computed(() => props.analysis?.overallLevel || '')
const expectedSalaryRange = computed(() => props.analysis?.expectedSalaryRange || '')

function scoreColor(s) {
  if (s === null || s === undefined) return '#909399'
  if (s >= 80) return '#67c23a'
  if (s >= 60) return '#e6a23c'
  return '#f56c6c'
}
</script>

<template>
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
          <span class="meta-value"> 答对 <b class="ok">{{ correctCount }}</b> / {{ totalCount }} 题 </span>
        </div>
      </template>

      <template v-else-if="sessionType === 'interview'">
        <div class="meta-item">
          <span class="meta-label">对话轮次</span>
          <span class="meta-value">{{ messageCount }} 条消息</span>
        </div>
        <div class="meta-item">
          <el-button type="primary" plain size="small" :icon="ChatDotRound" @click="emit('view-dialog')">
            查看完整对话
          </el-button>
        </div>
      </template>
    </div>
  </section>
</template>

<style scoped>
.card {
  background: #fff;
  border-radius: 14px;
  padding: 24px 26px;
  box-shadow: 0 2px 12px rgba(31, 45, 61, 0.06);
  border: 1px solid #ebeef5;
}
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

@media (max-width: 720px) {
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
</style>
