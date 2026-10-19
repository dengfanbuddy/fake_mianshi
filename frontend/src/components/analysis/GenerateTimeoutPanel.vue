<script setup>
import { WarningFilled, Refresh } from '@element-plus/icons-vue'

// 生成超时/失败面板：提示 + 重试按钮
defineProps({
  // 失败原因（SSE error 或超时）
  failReason: { type: String, default: '' },
  // 生成总超时秒数（仅用于提示文案）
  generateTimeout: { type: Number, default: 420 },
})

const emit = defineEmits(['refresh'])
</script>

<template>
  <div class="generating-panel">
    <el-icon :size="44" color="#e6a23c"><WarningFilled /></el-icon>
    <div class="gen-title">分析生成未完成</div>
    <div class="gen-sub">{{ failReason || '已等待超过 ' + generateTimeout + ' 秒仍未完成，可稍后刷新页面查看。' }}</div>
    <div class="fail-actions">
      <el-button type="primary" :icon="Refresh" @click="emit('refresh')">重新生成分析</el-button>
    </div>
  </div>
</template>

<style scoped>
.generating-panel {
  background: #fff;
  border-radius: 12px;
  padding: 20px 24px;
  display: flex;
  flex-direction: column;
  align-items: stretch;
  gap: 12px;
  box-shadow: 0 2px 10px rgba(0, 0, 0, 0.05);
}
.gen-title {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
  flex: 1;
}
.gen-sub {
  font-size: 12px;
  color: #909399;
  text-align: center;
}
.fail-actions {
  margin-top: 6px;
}
</style>
