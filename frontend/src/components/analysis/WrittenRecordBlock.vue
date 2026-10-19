<script setup>
// 笔试原始记录（答案 / 正确答案 / 解析）：纯展示
defineProps({
  // 笔试单题结果记录
  written: { type: Object, default: null },
})

/**
 * 格式化答案展示。
 *
 * <p>与 WrittenTestResult.vue 保持一致：只有多选题的 "AB" 才拆成 "A、B"。
 * 填空 / 简答答案可能是纯字母单词（"HashMap"），不按题型区分会被错误拆散。
 */
function formatAnswer(value, type) {
  if (value === undefined || value === null || value === '') return '未作答'
  if (
    type === 'MULTIPLE_CHOICE' &&
    typeof value === 'string' &&
    /^[A-Za-z]+$/.test(value) &&
    value.length > 1
  ) {
    return value.split('').join('、')
  }
  return value
}
</script>

<template>
  <template v-if="written">
    <el-divider class="written-divider">笔试记录</el-divider>
    <div v-if="written.type" class="written-meta">
      <el-tag size="small" type="info" effect="plain">{{ written.type }}</el-tag>
      <span class="written-score">得分：{{ written.score ?? 0 }} 分</span>
    </div>
    <div class="answer-row">
      <span class="qa-label">你的答案</span>
      <span :class="['answer-val', { wrong: written.isCorrect === false }]">
        {{ formatAnswer(written.userAnswer, written.type) }}
      </span>
    </div>
    <div v-if="written.isCorrect !== true" class="answer-row">
      <span class="qa-label">正确答案</span>
      <span class="answer-val correct">{{ formatAnswer(written.correctAnswer, written.type) }}</span>
    </div>
    <div v-if="written.explanation" class="qa-block">
      <span class="qa-label">解析</span>
      <p class="qa-text explain-text">{{ written.explanation }}</p>
    </div>
  </template>
</template>

<style scoped>
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
.qa-label {
  display: inline-block;
  font-size: 12px;
  color: #909399;
  margin-bottom: 4px;
  background: #f4f4f5;
  padding: 1px 8px;
  border-radius: 4px;
}
.qa-block {
  margin-bottom: 12px;
}
.qa-text {
  margin: 0;
  font-size: 14px;
  line-height: 1.75;
  color: #303133;
  white-space: pre-wrap;
  word-break: break-word;
}
.explain-text {
  background: #f4f4f5;
  padding: 10px 12px;
  border-radius: 6px;
}
</style>
