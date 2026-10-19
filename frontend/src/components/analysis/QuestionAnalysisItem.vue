<script setup>
import { computed, ref } from 'vue'
import WrittenRecordBlock from './WrittenRecordBlock.vue'

// 单题分析正文：题干 / 回答 / 维度评分 / 思路 / 示例 / 语气 / 建议（纯展示）
const props = defineProps({
  // 题目序号（0 基）
  index: { type: Number, required: true },
  // 单题分析结果
  analysis: { type: Object, default: null },
  // 笔试原始记录
  written: { type: Object, default: null },
})

const MAX_PREVIEW = 200
const DIMENSIONS = [
  { key: 'accuracy', reasonKey: 'accuracyReason', label: '准确性' },
  { key: 'depth', reasonKey: 'depthReason', label: '深度' },
  { key: 'clarity', reasonKey: 'clarityReason', label: '清晰度' },
  { key: 'fluency', reasonKey: 'fluencyReason', label: '流畅度' },
]

// 回答全文展开状态（仅本组件内使用，视觉行为不变）
const expanded = ref(false)
const answerText = computed(() => props.analysis?.answerContent || '')

function answerPreview(text) {
  const t = text || ''
  return t.length > MAX_PREVIEW ? `${t.slice(0, MAX_PREVIEW)}…` : t
}

function toggleExpand() {
  expanded.value = !expanded.value
}

// 该题有打分依据的维度列表
const dimsWithReasons = computed(() => DIMENSIONS.filter((d) => props.analysis && props.analysis[d.reasonKey]))

function dimColor(v) {
  const s = Number(v) || 0
  if (s >= 8) return '#67c23a'
  if (s >= 6) return '#e6a23c'
  return '#f56c6c'
}

// 难度标签颜色
function difficultyType(d) {
  const t = String(d || '')
  if (t.includes('初级')) return 'success'
  if (t.includes('高级')) return 'danger'
  return 'warning'
}
</script>

<template>
  <div class="qa-body">
    <!-- 题干 + 分类标签 -->
    <div v-if="analysis?.questionContent" class="qa-block">
      <span class="qa-label">题干</span>
      <p class="qa-text">{{ analysis.questionContent }}</p>
      <div v-if="analysis.category || analysis.difficulty || analysis.focusPoint" class="qa-tags">
        <el-tag v-if="analysis.category" size="small" type="primary" effect="plain">{{ analysis.category }}</el-tag>
        <el-tag v-if="analysis.difficulty" size="small" :type="difficultyType(analysis.difficulty)" effect="plain">{{ analysis.difficulty }}</el-tag>
        <el-tag v-if="analysis.focusPoint" size="small" type="info" effect="plain">考察：{{ analysis.focusPoint }}</el-tag>
      </div>
    </div>

    <!-- 你的回答 -->
    <div v-if="analysis?.answerContent" class="qa-block">
      <span class="qa-label">你的回答</span>
      <p class="qa-text">{{ expanded ? analysis.answerContent : answerPreview(analysis.answerContent) }}</p>
      <el-button
        v-if="answerText.length > MAX_PREVIEW"
        link
        type="primary"
        size="small"
        class="expand-btn"
        @click="toggleExpand"
      >
        {{ expanded ? '收起' : '展开全文' }}
      </el-button>
    </div>

    <!-- 各维度评分（附打分依据） -->
    <div v-if="analysis" class="dims">
      <div v-for="d in DIMENSIONS" :key="d.key" class="dim-row">
        <span class="dim-name">{{ d.label }}</span>
        <el-progress
          class="dim-bar"
          :percentage="Math.round((Number(analysis[d.key]) || 0) * 10)"
          :stroke-width="8"
          :color="dimColor(analysis[d.key])"
          :show-text="false"
        />
        <span class="dim-score" :style="{ color: dimColor(analysis[d.key]) }">
          {{ Number(analysis[d.key]) || 0 }}/10
        </span>
      </div>
      <div v-if="dimsWithReasons.length" class="dim-reasons">
        <div v-for="d in dimsWithReasons" :key="d.key" class="dim-reason">
          <span class="dim-reason-label">{{ d.label }}：</span>
          <span class="dim-reason-text">{{ analysis[d.reasonKey] }}</span>
        </div>
      </div>
    </div>

    <!-- 回答思路 -->
    <div v-if="analysis?.answerApproach" class="qa-block">
      <span class="qa-label">这类题的回答思路</span>
      <p class="qa-text approach-text">{{ analysis.answerApproach }}</p>
    </div>

    <!-- 优秀回答示例 -->
    <div v-if="analysis?.example" class="qa-block">
      <span class="qa-label">优秀回答示例</span>
      <p class="qa-text example-text">{{ analysis.example }}</p>
    </div>

    <!-- 语气 / 改进建议 -->
    <div v-if="analysis?.toneEvaluation" class="qa-block">
      <span class="qa-label">语气评价</span>
      <p class="qa-text">{{ analysis.toneEvaluation }}</p>
    </div>
    <div v-if="analysis?.improvementSuggestion" class="qa-block">
      <span class="qa-label">改进建议</span>
      <p class="qa-text suggest-text">{{ analysis.improvementSuggestion }}</p>
    </div>

    <!-- 笔试原始记录 -->
    <WrittenRecordBlock v-if="written" :written="written" />
  </div>
</template>

<style scoped>
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
.suggest-text {
  color: #b88230;
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
</style>
