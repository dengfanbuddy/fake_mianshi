<script setup>
// 项目头部信息卡：项目名 + 描述 + 编辑/删除入口
defineProps({
  project: {
    type: Object,
    default: null,
  },
})

defineEmits(['edit', 'delete'])
</script>

<template>
  <div class="page-header">
    <div class="header-left">
      <h2 class="project-name">{{ project?.name || '项目详情' }}</h2>
      <p class="project-desc">{{ project?.description || '暂无描述' }}</p>
    </div>
    <div class="header-actions">
      <el-button @click="$emit('edit')">编辑</el-button>
      <el-popconfirm title="确定删除该项目吗？删除后关联数据将一并清除" confirm-button-text="删除" cancel-button-text="取消" @confirm="$emit('delete')">
        <template #reference>
          <el-button type="danger" plain>删除</el-button>
        </template>
      </el-popconfirm>
    </div>
  </div>
</template>

<style scoped>
.page-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  margin-bottom: 20px;
}
.header-left {
  flex: 1;
  min-width: 0;
}
.project-name {
  margin: 0 0 6px;
  font-size: 22px;
}
.project-desc {
  margin: 0;
  color: #606266;
  font-size: 14px;
}
.header-actions {
  flex-shrink: 0;
  display: flex;
  gap: 8px;
}
</style>
