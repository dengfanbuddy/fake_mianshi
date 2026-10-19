<script setup>
/**
 * 换面试官弹窗（手动切换）：只负责展示与选择，切换请求由父组件发起。
 */
defineProps({
  // 弹窗可见性（v-model:visible）
  visible: { type: Boolean, default: false },
  personas: { type: Array, default: () => [] },
  // 待切换到的面试官 id
  personaId: { type: [String, Number], default: null },
  // 切换请求中
  switching: { type: Boolean, default: false },
})

const emit = defineEmits(['update:visible', 'change-persona', 'confirm'])
</script>

<template>
  <el-dialog
    :model-value="visible"
    title="切换面试官"
    width="420px"
    @update:model-value="emit('update:visible', $event)"
  >
    <p class="dialog-tip">切换后，后续提问将按新面试官的风格进行。</p>
    <el-select
      :model-value="personaId"
      placeholder="选择新的面试官风格"
      style="width: 100%"
      @update:model-value="emit('change-persona', $event)"
    >
      <el-option v-for="p in personas" :key="p.id" :label="p.name" :value="p.id">
        <span>{{ p.name }}</span>
        <span class="option-desc">{{ p.description || '' }}</span>
      </el-option>
    </el-select>
    <template #footer>
      <el-button @click="emit('update:visible', false)">取消</el-button>
      <el-button type="primary" :loading="switching" @click="emit('confirm')">确认切换</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.dialog-tip {
  margin: 0 0 12px;
  color: #909399;
  font-size: 13px;
}
.option-desc {
  float: right;
  font-size: 12px;
  color: #909399;
  margin-left: 16px;
  max-width: 180px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
