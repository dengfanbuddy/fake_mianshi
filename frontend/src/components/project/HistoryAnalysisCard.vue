<script setup>
// 综合分析入口卡：跳转历史综合分析页
defineEmits(['open'])
</script>

<template>
  <el-card class="section" shadow="never">
    <template #header><span>综合分析</span></template>
    <div class="section-body history-analysis">
      <p class="muted">查看本项目的整体成长分析与各轮面试对比。</p>
      <el-button type="primary" @click="$emit('open')">查看历史综合分析</el-button>
    </div>
  </el-card>
</template>

<style scoped>
.section {
  margin-bottom: 20px;
}
.muted {
  color: #c0c4cc;
}

/* 历史分析 */
.history-analysis p {
  margin: 0 0 12px;
}
</style>
