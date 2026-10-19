<script setup>
/**
 * 开场控制条：无会话时显示的配置卡片（选择面试官风格 + 是否引用笔试结果）。
 * 纯展示 + 事件上抛，接口调用由父组件编排。
 */
defineProps({
  // 当前选中的面试官风格 id（v-model）
  modelValue: { type: [String, Number], default: null },
  personas: { type: Array, default: () => [] },
  // 是否引用最近一次笔试结果
  referenceWrittenTest: { type: Boolean, default: false },
  // 开始面试请求中
  starting: { type: Boolean, default: false },
})

const emit = defineEmits(['update:modelValue', 'toggle-reference-written-test', 'start', 'back'])
</script>

<template>
  <div class="config-wrap">
    <div class="config-card">
      <h2 class="config-title">AI 模拟面试</h2>
      <p class="config-subtitle">与 AI 面试官一对一对话，支持语音回答</p>

      <el-form label-width="90px" label-position="left" class="config-form">
        <el-form-item label="面试官风格">
          <el-select
            :model-value="modelValue"
            placeholder="选择面试官风格"
            style="width: 100%"
            clearable
            @update:model-value="emit('update:modelValue', $event)"
          >
            <el-option v-for="p in personas" :key="p.id" :label="p.name" :value="p.id">
              <span>{{ p.name }}</span>
              <span class="option-desc">{{ p.description || '' }}</span>
            </el-option>
            <template v-if="!personas.length">
              <el-option value="" label="系统推荐" disabled />
            </template>
          </el-select>
          <div v-if="!personas.length" class="form-tip">未获取到面试官列表，将使用系统推荐风格</div>
        </el-form-item>
        <el-form-item label="笔试结合">
          <el-checkbox
            :model-value="referenceWrittenTest"
            @update:model-value="emit('toggle-reference-written-test', $event)"
          >
            引用最近一次笔试结果，针对薄弱知识点重点考察
          </el-checkbox>
        </el-form-item>
      </el-form>

      <div class="config-actions">
        <el-button @click="emit('back')">返回</el-button>
        <el-button type="primary" :loading="starting" @click="emit('start')">开始面试</el-button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.config-wrap {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
}
.config-card {
  width: 520px;
  background: #fff;
  border-radius: 12px;
  padding: 36px 36px 28px;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.08);
}
.config-title {
  margin: 0 0 8px;
  font-size: 22px;
  text-align: center;
}
.config-subtitle {
  margin: 0 0 28px;
  text-align: center;
  color: #909399;
  font-size: 13px;
}
.config-form {
  margin-bottom: 8px;
}
.form-tip {
  font-size: 12px;
  color: #909399;
  margin-top: 6px;
}
.config-actions {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
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
