<script setup>
// 改进方案（学习方向时间线 + 具体建议）：纯展示
defineProps({
  // 学习方向（对象 {topic, action, example} 或旧版纯字符串）
  planTopics: { type: Array, default: () => [] },
  // 具体建议（纯字符串）
  planSuggestions: { type: Array, default: () => [] },
})

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
</script>

<template>
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
</style>
