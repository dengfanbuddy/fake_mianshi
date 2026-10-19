<script setup>
defineProps({
  tags: {
    type: Array,
    default: () => [],
  },
  loading: {
    type: Boolean,
    default: false,
  },
})

const masteryTagMap = {
  WEAK: { type: 'danger', label: '薄弱' },
  FAIR: { type: 'warning', label: '一般' },
  GOOD: { type: 'success', label: '良好' },
  STRONG: { type: 'primary', label: '优秀' },
}

function masteryMeta(level) {
  return masteryTagMap[level] || { type: 'info', label: level || '未知' }
}
</script>

<template>
  <el-card class="section" shadow="never">
    <template #header><span>薄弱知识点</span></template>

    <div v-loading="loading" class="section-body">
      <div v-if="tags.length" class="weakness-list">
        <div v-for="tag in tags" :key="tag.id" class="weakness-item">
          <span class="weak-point">{{ tag.knowledgePoint }}</span>
          <el-tag :type="masteryMeta(tag.masteryLevel).type" size="small" effect="dark">
            {{ masteryMeta(tag.masteryLevel).label }}
          </el-tag>
          <el-badge
            :value="tag.occurrenceCount"
            :max="99"
            class="occurrence-badge"
          ><span class="occurrence-bg">出现次数</span></el-badge>
        </div>
      </div>
      <el-empty v-else-if="!loading" description="暂无弱点标签，完成面试分析后生成" />
    </div>
  </el-card>
</template>

<style scoped>
.section {
  margin-bottom: 20px;
}

/* 弱点 */
.weakness-list {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
}
.weakness-item {
  display: flex;
  align-items: center;
  gap: 8px;
  background: #f9fafb;
  border-radius: 8px;
  padding: 10px 14px;
}
.weak-point {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
}
.occurrence-badge {
  margin-left: 4px;
}
.occurrence-bg {
  display: inline-block;
  font-size: 12px;
  color: #909399;
}
</style>
