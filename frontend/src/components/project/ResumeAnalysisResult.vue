<script setup>
// 简历 AI 分析结果展示
defineProps({
  analysis: {
    type: Object,
    default: null,
  },
})
</script>

<template>
  <div v-if="analysis" class="analysis-result">
    <el-divider content-position="left">简历 AI 分析结果</el-divider>
    <div class="analysis-grid">
      <div class="analysis-item">
        <span class="label">经验年限</span>
        <span class="value">{{ analysis.experienceYears ?? '—' }}</span>
      </div>
      <div class="analysis-item">
        <span class="label">当前职位</span>
        <span class="value">{{ analysis.currentPosition || '—' }}</span>
      </div>
      <div class="analysis-item">
        <span class="label">建议岗位</span>
        <span class="value highlight">{{ analysis.suggestedPosition || '—' }}</span>
      </div>
      <div class="analysis-item">
        <span class="label">建议职级</span>
        <span class="value">{{ analysis.suggestedSeniority || '—' }}</span>
      </div>
    </div>

    <div class="analysis-block">
      <div class="block-label">技术栈</div>
      <div class="tag-list">
        <el-tag
          v-for="(t, i) in (analysis.techStack || [])"
          :key="i"
          size="small"
          class="tag-item"
        >{{ t }}</el-tag>
        <span v-if="!(analysis.techStack || []).length" class="muted">—</span>
      </div>
    </div>

    <div class="analysis-block">
      <div class="block-label">项目经验</div>
      <ul v-if="(analysis.projects || []).length" class="project-list">
        <li v-for="(p, i) in analysis.projects" :key="i">{{ p }}</li>
      </ul>
      <span v-else class="muted">—</span>
    </div>

    <div v-if="analysis.education" class="analysis-block">
      <div class="block-label">教育背景</div>
      <div>{{ analysis.education }}</div>
    </div>

    <div v-if="analysis" class="confirm-tip">
      <el-alert
        type="info"
        :closable="false"
        show-icon
        title="确认分析结果符合你的求职方向后，可一键生成目标岗位需求，用于后续笔试与模拟面试的题目定制。"
      />
    </div>
  </div>
</template>

<style scoped>
.analysis-result {
  margin-top: 16px;
}
.analysis-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
  gap: 12px;
  margin-bottom: 16px;
}
.analysis-item {
  background: #f9fafb;
  border-radius: 8px;
  padding: 12px 14px;
}
.analysis-item .label {
  display: block;
  font-size: 12px;
  color: #909399;
  margin-bottom: 4px;
}
.analysis-item .value {
  font-size: 15px;
  font-weight: 600;
  color: #303133;
}
.analysis-item .value.highlight {
  color: #409eff;
}
.analysis-block {
  margin-bottom: 12px;
}
.block-label {
  font-size: 13px;
  font-weight: 600;
  color: #606266;
  margin-bottom: 6px;
}
.tag-list {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
.tag-item {
  margin-right: 4px;
}
.project-list {
  margin: 0;
  padding-left: 20px;
  line-height: 1.8;
  color: #303133;
}
.confirm-tip {
  margin-top: 8px;
}
.muted {
  color: #c0c4cc;
}
</style>
