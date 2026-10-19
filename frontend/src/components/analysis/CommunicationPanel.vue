<script setup>
// 沟通表达评价（仅面试会话展示）：纯展示
defineProps({
  communicationEvaluation: { type: String, default: '' },
})
</script>

<template>
  <section v-if="communicationEvaluation" class="card">
    <h3 class="card-title info-title">💬 沟通表达评价</h3>
    <p class="para-text">{{ communicationEvaluation }}</p>
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
.card-title.info-title {
  color: #337ecc;
}
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
</style>
