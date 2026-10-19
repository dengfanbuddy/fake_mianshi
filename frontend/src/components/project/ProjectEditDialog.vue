<script setup>
import { ref, watch } from 'vue'

const props = defineProps({
  visible: {
    type: Boolean,
    default: false,
  },
  project: {
    type: Object,
    default: null,
  },
  saving: {
    type: Boolean,
    default: false,
  },
})

const emit = defineEmits(['update:visible', 'submit'])

const form = ref({ name: '', targetPosition: '', description: '' })

// 打开弹窗时用当前项目信息回填表单
watch(
  () => props.visible,
  (v) => {
    if (!v) return
    form.value = {
      name: props.project?.name || '',
      targetPosition: props.project?.targetPosition || '',
      description: props.project?.description || '',
    }
  }
)

function handleSubmit() {
  emit('submit', {
    name: (form.value.name || '').trim(),
    targetPosition: (form.value.targetPosition || '').trim(),
    description: (form.value.description || '').trim(),
  })
}
</script>

<template>
  <el-dialog
    :model-value="visible"
    title="编辑项目"
    width="480px"
    @update:model-value="emit('update:visible', $event)"
  >
    <el-form label-width="80px">
      <el-form-item label="项目名称" required>
        <el-input v-model="form.name" maxlength="100" />
      </el-form-item>
      <el-form-item label="目标岗位">
        <el-input v-model="form.targetPosition" maxlength="200" placeholder="如：Java 后端高级工程师" />
      </el-form-item>
      <el-form-item label="描述">
        <el-input v-model="form.description" type="textarea" :rows="3" maxlength="500" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="emit('update:visible', false)">取消</el-button>
      <el-button type="primary" :loading="saving" @click="handleSubmit">保存</el-button>
    </template>
  </el-dialog>
</template>
