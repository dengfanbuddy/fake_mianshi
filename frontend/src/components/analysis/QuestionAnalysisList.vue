<script setup>
import QuestionAnalysisItem from './QuestionAnalysisItem.vue'

// 逐题深度分析：折叠面板 + 标题行（题目正文交给 QuestionAnalysisItem 展示）
defineProps({
  // 单题分析 + 笔试原始记录按顺序对齐后的列表 [{ index, analysis, written }]
  questions: { type: Array, default: () => [] },
})

// 展开项（父组件传入并回写，保持原有「默认全部展开」行为）
const activeNames = defineModel({ type: Array, default: () => [] })
</script>

<template>
  <section v-if="questions.length" class="card">
    <h3 class="card-title">📝 逐题深度分析</h3>
    <el-collapse v-model="activeNames" class="qa-collapse">
      <el-collapse-item v-for="q in questions" :key="q.index" :name="q.index">
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

        <QuestionAnalysisItem :index="q.index" :analysis="q.analysis" :written="q.written" />
      </el-collapse-item>
    </el-collapse>
  </section>

  <el-empty v-if="!questions.length" description="暂无逐题分析" />
</template>

<style scoped>
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
</style>
