<script setup>
defineProps({
  // 全部会话列表（父组件按项目加载）
  sessions: {
    type: Array,
    default: () => [],
  },
  loading: {
    type: Boolean,
    default: false,
  },
  // 进行中的会话（父组件计算后传入）
  inProgressSessions: {
    type: Array,
    default: () => [],
  },
})

const emit = defineEmits([
  'start-written',
  'start-mock',
  'start-full-flow',
  'continue',
  'view-report',
  'delete',
])

function typeText(type) {
  return { WRITTEN: '笔试', MOCK: '模拟面试', BOTH: '笔试+面试' }[type] || type || '—'
}

function statusText(status) {
  return { PENDING: '待开始', IN_PROGRESS: '进行中', COMPLETED: '已完成', ABANDONED: '已放弃' }[status] || status || '—'
}

function statusTagType(status) {
  return { PENDING: 'info', IN_PROGRESS: 'warning', COMPLETED: 'success', ABANDONED: 'info' }[status] || 'info'
}

function typeTagType(type) {
  return { WRITTEN: 'primary', MOCK: 'success', BOTH: 'warning' }[type] || 'info'
}

function formatTime(s) {
  if (!s) return '—'
  return new Date(s).toLocaleString()
}
</script>

<template>
  <el-card class="section" shadow="never">
    <template #header><span>面试历史</span></template>

    <div class="section-body">
      <div class="start-buttons">
        <el-button type="primary" @click="emit('start-written')">📝 笔试</el-button>
        <el-button type="success" @click="emit('start-mock')">🗣️ 模拟面试</el-button>
        <el-button type="warning" @click="emit('start-full-flow')">🚀 完整流程</el-button>
      </div>

      <!-- 进行中的考试：可继续 -->
      <div v-if="inProgressSessions.length" class="resume-bar">
        <div class="resume-title">⏳ 进行中的考试</div>
        <div v-for="s in inProgressSessions" :key="s.id" class="resume-item">
          <el-tag :type="typeTagType(s.type)" size="small" effect="dark">
            {{ s.type === 'WRITTEN' ? '笔试' : '模拟面试' }}
          </el-tag>
          <span class="resume-info">已开始于 {{ formatTime(s.startedAt) }}</span>
          <el-button type="primary" size="small" plain @click="emit('continue', s)">
            {{ s.type === 'WRITTEN' ? '继续笔试' : '继续面试' }}
          </el-button>
        </div>
      </div>

      <el-table
        v-if="sessions.length"
        v-loading="loading"
        :data="sessions"
        border
        stripe
        class="session-table"
      >
        <el-table-column label="类型" width="130">
          <template #default="{ row }">
            <el-tag :type="typeTagType(row.type)" size="small" effect="dark">{{ typeText(row.type) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="120">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)" size="small">{{ statusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="时长限制" width="110">
          <template #default="{ row }">
            <span v-if="row.timeLimit">{{ row.timeLimit }} 分钟</span>
            <span v-else class="muted">—</span>
          </template>
        </el-table-column>
        <el-table-column label="开始时间" min-width="170">
          <template #default="{ row }">{{ formatTime(row.startedAt || row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="完成时间" min-width="170">
          <template #default="{ row }">{{ formatTime(row.completedAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="160">
          <template #default="{ row }">
            <el-button type="primary" link @click="emit('view-report', row.id)">查看报告</el-button>
            <el-popconfirm
              title="删除该面试及其录音？"
              confirm-button-text="删除"
              cancel-button-text="取消"
              @confirm="emit('delete', row.id)"
            >
              <template #reference>
                <el-button type="danger" link>删除</el-button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>

      <el-empty v-if="!loading && !sessions.length" description="暂无面试记录，点击上方按钮开始第一次面试" />
    </div>
  </el-card>
</template>

<style scoped>
.section {
  margin-bottom: 20px;
}

/* 面试历史 */
.start-buttons {
  display: flex;
  gap: 12px;
  margin-bottom: 16px;
}
.session-table {
  margin-top: 8px;
}
.resume-bar {
  background: #fdf6ec;
  border: 1px solid #f3d19e;
  border-radius: 8px;
  padding: 12px 16px;
  margin-bottom: 14px;
}
.resume-title {
  font-size: 13px;
  font-weight: 600;
  color: #b88230;
  margin-bottom: 8px;
}
.resume-item {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 8px;
}
.resume-item:last-child {
  margin-bottom: 0;
}
.resume-bar .resume-info {
  font-size: 13px;
  color: #606266;
  flex: 1;
}
.muted {
  color: #c0c4cc;
}
</style>
