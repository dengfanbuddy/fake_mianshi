<script setup>
import { Loading } from '@element-plus/icons-vue'

// 生成中面板：SSE 流式实时展示（直接显示原文）
defineProps({
  // 标题文案（由 useSessionAnalysis 的 generatingTitle 提供）
  title: { type: String, default: '' },
  // 已等待秒数
  elapsed: { type: Number, default: 0 },
  // AI 正式输出内容（原文实时显示）
  streamText: { type: String, default: '' },
  // 思考型模型的思考过程
  streamReasoning: { type: String, default: '' },
  // 输出停滞提示
  stalled: { type: Boolean, default: false },
  // 空闲超时秒数（仅用于提示文案）
  idleTimeout: { type: Number, default: 60 },
})
</script>

<template>
  <div class="generating-panel">
    <div class="gen-header">
      <el-icon :size="20" class="is-loading" color="#409eff"><Loading /></el-icon>
      <span class="gen-title">{{ title }}</span>
      <span class="gen-elapsed">已等待 {{ elapsed }} 秒 · 已接收 {{ streamText.length }} 字</span>
    </div>
    <div class="gen-stream raw-text">
      <template v-if="streamText">{{ streamText }}</template>
      <template v-else-if="streamReasoning">
        <div class="reasoning-box">
          <div class="reasoning-hint">🤔 AI 正在思考中（已思考 {{ streamReasoning.length }} 字）…</div>
          <div class="reasoning-text">{{ streamReasoning }}</div>
        </div>
      </template>
      <template v-else-if="stalled">AI 输出似乎停滞（{{ idleTimeout }} 秒无新内容），仍在等待…</template>
      <template v-else>AI 正在生成分析报告，请稍候（自动刷新等待中）…</template>
    </div>
    <div class="gen-sub">生成完成后自动切换为格式化报告，无需操作。</div>
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
.gen-header {
  display: flex;
  align-items: center;
  gap: 10px;
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
.gen-elapsed {
  font-size: 12px;
  color: #909399;
  white-space: nowrap;
}
.gen-stream {
  max-height: 52vh;
  overflow-y: auto;
  background: #fafbfc;
  border: 1px solid #ebeef5;
  border-radius: 10px;
  padding: 14px 18px;
  font-size: 13px;
  line-height: 1.8;
  color: #303133;
}
.gen-stream.raw-text {
  white-space: pre-wrap;
  word-break: break-all;
  overflow-wrap: anywhere;
}
.reasoning-box {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.reasoning-hint {
  font-size: 13px;
  color: #e6a23c;
  font-weight: 600;
}
.reasoning-text {
  font-size: 12px;
  color: #909399;
  line-height: 1.7;
  max-height: 40vh;
  overflow-y: auto;
}
.gen-stream h1,
.gen-stream h2,
.gen-stream h3,
.gen-stream h4 {
  font-size: 14px;
  margin: 10px 0 4px;
  color: #303133;
}
.gen-stream ul,
.gen-stream ol {
  margin: 4px 0;
  padding-left: 20px;
}
.gen-stream p {
  margin: 4px 0;
}
.gen-stream code {
  background: #f0f2f5;
  border-radius: 3px;
  padding: 1px 5px;
  font-size: 12px;
}
</style>
