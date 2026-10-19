<script setup>
/**
 * 面试标题栏 + 大纲折叠面板。
 * 大纲解析（后端返回 JSON 字符串，可能被包在 markdown 代码块里）也在这里完成，
 * 父组件只负责把原始 outline 透传进来。
 */
import { computed } from 'vue'

const props = defineProps({
  sessionId: { type: [String, Number], default: null },
  // 当前面试官名称
  personaName: { type: String, default: '面试官' },
  // 面试是否已结束
  ended: { type: Boolean, default: false },
  // 后端返回的大纲原文（JSON 字符串）
  outline: { type: String, default: '' },
})

// 大纲解析：后端返回 JSON 字符串，剥离可能的 markdown 代码块
const outlineSections = computed(() => {
  if (!props.outline) return []
  try {
    let obj
    try {
      obj = JSON.parse(props.outline)
    } catch {
      obj = JSON.parse(props.outline.replace(/```(json)?/g, '').trim())
    }
    if (!obj || typeof obj !== 'object') return []
    const list = []
    if (Array.isArray(obj.sections)) {
      const labels = { TECHNICAL: '技术能力', PROJECT: '项目深挖', BEHAVIORAL: '行为面试' }
      obj.sections.forEach((s) => {
        list.push({
          type: labels[s.type] || s.type || '面试环节',
          topics: Array.isArray(s.topics) ? s.topics : [],
        })
      })
    }
    if (obj.estimatedQuestions) list.push({ type: '预计题量', topics: [`约 ${obj.estimatedQuestions} 题`] })
    if (Array.isArray(obj.focusPoints) && obj.focusPoints.length) {
      list.push({ type: '重点考察', topics: obj.focusPoints })
    }
    return list
  } catch {
    return []
  }
})

function formatOutlineRow(content) {
  if (!content) return ''
  return content.replace(/^[-*•]\s*/, '')
}
</script>

<template>
  <header class="chat-header">
    <div class="header-left">
      <h2 class="header-title">AI 模拟面试</h2>
      <el-tag size="small" effect="dark" type="primary">{{ personaName }}</el-tag>
      <el-tag v-if="ended" size="small" type="success" effect="dark">已结束</el-tag>
    </div>
    <div class="header-right">
      <span class="session-id" v-if="sessionId">会话 #{{ sessionId }}</span>
    </div>
  </header>

  <!-- 大纲折叠面板 -->
  <div class="outline-wrap" v-if="outline">
    <el-collapse class="outline-collapse">
      <el-collapse-item>
        <template #title>
          <span class="outline-title">📋 本次面试大纲</span>
        </template>
        <div class="outline-body">
          <template v-if="outlineSections.length">
            <div v-for="(sec, i) in outlineSections" :key="i" class="outline-sec">
              <div class="outline-type">{{ sec.type }}</div>
              <ul class="outline-topics">
                <li v-for="(t, j) in sec.topics" :key="j">{{ formatOutlineRow(t) }}</li>
              </ul>
            </div>
          </template>
          <div v-else class="outline-raw">{{ outline }}</div>
        </div>
      </el-collapse-item>
    </el-collapse>
  </div>
</template>

<style scoped>
.chat-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 20px;
  background: #fff;
  border-bottom: 1px solid #ebeef5;
  flex-shrink: 0;
}
.header-left {
  display: flex;
  align-items: center;
  gap: 10px;
}
.header-title {
  margin: 0;
  font-size: 17px;
}
.header-right .session-id {
  font-size: 12px;
  color: #909399;
}

/* ---------- 大纲 ---------- */
.outline-wrap {
  flex-shrink: 0;
  background: #fff;
  border-bottom: 1px solid #ebeef5;
}
.outline-collapse {
  border: none;
}
.outline-collapse :deep(.el-collapse-item__header) {
  background: transparent;
  height: 40px;
  padding-left: 20px;
}
.outline-title {
  font-weight: 600;
  color: #303133;
}
.outline-body {
  padding: 4px 24px 14px;
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}
.outline-sec {
  border: 1px solid #ebeef5;
  border-radius: 8px;
  padding: 8px 12px;
  min-width: 200px;
  background: #fafbfc;
}
.outline-type {
  font-weight: 600;
  font-size: 13px;
  color: #409eff;
  margin-bottom: 4px;
}
.outline-topics {
  margin: 0;
  padding-left: 18px;
  font-size: 12px;
  color: #606266;
}
.outline-raw {
  font-size: 12px;
  color: #606266;
  white-space: pre-wrap;
  width: 100%;
}
</style>
