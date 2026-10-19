<script setup>
import { WarningFilled } from '@element-plus/icons-vue'

// 知识盲区列表：纯展示
defineProps({
  // 盲区项（对象 {point, explanation} 或旧版纯字符串）
  knowledgeGaps: { type: Array, default: () => [] },
})

// 知识盲区：兼容对象 {point, explanation} 与旧版纯字符串
function gapPoint(g) {
  return typeof g === 'object' && g !== null ? g.point || '' : g || ''
}
function gapExplanation(g) {
  return typeof g === 'object' && g !== null ? g.explanation || '' : ''
}
</script>

<template>
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
.card-title.warn-title {
  color: #b88230;
}
.title-icon {
  font-size: 16px;
}
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
</style>
