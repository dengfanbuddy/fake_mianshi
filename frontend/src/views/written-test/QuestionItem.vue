<script setup>
/**
 * 单道题目（题干 + 按题型渲染的作答控件）。
 * 只接收 modelValue 并往上 emit，不持有答案状态——答案归考试页统一管理（要持久化到 sessionStorage）。
 */
import { computed, ref } from 'vue'
import { renderMd, letter, optionText, parseOptions, TYPE_LABEL, TYPE_TAG } from './examHelpers'

const props = defineProps({
  question: { type: Object, required: true },
  index: { type: Number, required: true },
  modelValue: { type: [String, Array, Number], default: '' },
})
const emit = defineEmits(['update:modelValue'])

const rootRef = ref(null)
const options = computed(() => parseOptions(props.question))
const html = computed(() => renderMd(props.question.content))

function update(value) {
  emit('update:modelValue', value)
}

/** 答题卡点题号时滚动到本题（由父组件调用） */
function scrollIntoView(options) {
  rootRef.value?.scrollIntoView(options)
}

defineExpose({ scrollIntoView })
</script>

<template>
  <div ref="rootRef" class="question-item">
    <div class="q-head">
      <span class="q-index">{{ index + 1 }}</span>
      <el-tag :type="TYPE_TAG[question.type]" size="small" effect="plain">
        {{ TYPE_LABEL[question.type] || question.type }}
      </el-tag>
    </div>
    <div class="q-content markdown-body" v-html="html"></div>

    <!-- 单选题 -->
    <el-radio-group
      v-if="question.type === 'SINGLE_CHOICE'"
      :model-value="modelValue"
      class="q-options"
      @update:model-value="update"
    >
      <el-radio v-for="(opt, i) in options" :key="i" :value="letter(i)" class="q-option">
        <span class="opt-key">{{ letter(i) }}.</span> {{ optionText(opt, i) }}
      </el-radio>
    </el-radio-group>

    <!-- 多选题 -->
    <el-checkbox-group
      v-else-if="question.type === 'MULTIPLE_CHOICE'"
      :model-value="modelValue"
      class="q-options"
      @update:model-value="update"
    >
      <el-checkbox v-for="(opt, i) in options" :key="i" :value="letter(i)" class="q-option">
        <span class="opt-key">{{ letter(i) }}.</span> {{ optionText(opt, i) }}
      </el-checkbox>
    </el-checkbox-group>

    <!-- 填空题 -->
    <div v-else-if="question.type === 'FILL_BLANK'" class="q-input">
      <el-input :model-value="modelValue" placeholder="请输入你的答案" clearable @update:model-value="update" />
    </div>

    <!-- 简答题 -->
    <div v-else-if="question.type === 'SHORT_ANSWER'" class="q-input">
      <el-input
        :model-value="modelValue"
        type="textarea"
        :rows="4"
        placeholder="请输入你的回答"
        show-word-limit
        maxlength="2000"
        @update:model-value="update"
      />
    </div>
  </div>
</template>

<style scoped>
.question-item {
  padding: 20px 0;
  border-bottom: 1px solid #f0f2f5;
}
.question-item:last-child {
  border-bottom: none;
}
.q-head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
}
.q-index {
  width: 26px;
  height: 26px;
  border-radius: 50%;
  background: #409eff;
  color: #fff;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 13px;
  flex-shrink: 0;
}
.q-content {
  font-size: 15px;
  line-height: 1.6;
  color: #303133;
  margin-bottom: 14px;
  word-break: break-word;
}
.q-content :deep(h1), .q-content :deep(h2), .q-content :deep(h3), .q-content :deep(h4) {
  font-size: 15px;
  margin: 8px 0 4px;
}
.q-content :deep(ul), .q-content :deep(ol) {
  margin: 4px 0;
  padding-left: 20px;
}
.q-content :deep(p) {
  margin: 4px 0;
}
.q-content :deep(code) {
  background: #f0f2f5;
  border-radius: 3px;
  padding: 1px 5px;
  font-size: 13px;
}
.q-content :deep(pre) {
  background: #f6f8fa;
  border-radius: 6px;
  padding: 10px;
  overflow-x: auto;
}
.q-options {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 10px;
}
.q-option {
  display: flex;
  align-items: flex-start;
  height: auto;
  white-space: normal;
  line-height: 1.5;
}
.opt-key {
  font-weight: 600;
  margin-right: 2px;
}
.q-input {
  max-width: 600px;
}
</style>
