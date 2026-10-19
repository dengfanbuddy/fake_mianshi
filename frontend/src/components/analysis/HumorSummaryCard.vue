<script setup>
// 幽默总结卡（"面试官大实话"）：纯展示
defineProps({
  humorSummary: { type: String, default: '' },
})
</script>

<template>
  <section v-if="humorSummary" class="card humor-card">
    <div class="humor-emoji">😄</div>
    <div class="humor-content">
      <div class="humor-title">面试官大实话</div>
      <div class="humor-text">{{ humorSummary }}</div>
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
.humor-card {
  display: flex;
  align-items: center;
  gap: 14px;
  background: linear-gradient(135deg, #fdf6ec 0%, #fff7e6 100%);
  border: 1px solid #f3d19e;
  border-radius: 12px;
  padding: 16px 20px;
}
.humor-emoji {
  font-size: 34px;
  line-height: 1;
}
.humor-title {
  font-size: 13px;
  font-weight: 600;
  color: #b88230;
  margin-bottom: 4px;
}
.humor-text {
  font-size: 15px;
  color: #7a5c1e;
  line-height: 1.6;
}
</style>
