<script setup>
/**
 * 底部输入栏：录音按钮 + 文字输入 + 发送，以及底部工具条（自动朗读、换面试官、结束面试）。
 * 录音组件的 start / chunk / recorded 事件原样上抛，由父组件交给 useVoiceStream 处理。
 */
import AudioRecorder from '../AudioRecorder.vue'

defineProps({
  // 输入框文本（v-model）
  modelValue: { type: String, default: '' },
  // 面试官回复发送中
  sending: { type: Boolean, default: false },
  // STT 识别中
  recognizing: { type: Boolean, default: false },
  // 正在按住录音
  recording: { type: Boolean, default: false },
  // 面试已结束
  ended: { type: Boolean, default: false },
  // 结束面试请求中
  ending: { type: Boolean, default: false },
  // 是否自动朗读面试官回复
  autoTts: { type: Boolean, default: true },
  // 是否有会话（无会话时禁用换人/结束）
  sessionId: { type: [String, Number], default: null },
})

const emit = defineEmits([
  'update:modelValue',
  'toggle-auto-tts',
  'send',
  'record-start',
  'record-chunk',
  'record-recorded',
  'switch-persona',
  'end',
])
</script>

<template>
  <footer class="input-area" :class="{ disabled: ended }">
    <div v-if="ended" class="ended-banner">面试已结束，感谢参与</div>
    <div class="input-row">
      <AudioRecorder
        :class="{ 'input-disabled': sending || recognizing || ended }"
        @start="emit('record-start')"
        @chunk="emit('record-chunk', $event)"
        @recorded="emit('record-recorded', $event)"
      />
      <el-input
        :model-value="modelValue"
        class="text-input"
        type="textarea"
        :rows="1"
        :autosize="{ minRows: 1, maxRows: 4 }"
        resize="none"
        placeholder="输入你的回答（Shift+Enter 换行），或按住左侧按钮说话…"
        :disabled="sending || recognizing || recording || ended"
        @update:model-value="emit('update:modelValue', $event)"
        @keyup.enter.exact.prevent="emit('send')"
      />
      <el-button
        type="primary"
        :loading="sending"
        :disabled="recognizing || recording || ended"
        @click="emit('send')"
      >
        发送
      </el-button>
    </div>
    <div class="tool-row">
      <el-switch
        :model-value="autoTts"
        size="small"
        active-text="自动朗读面试官回复"
        :disabled="ended"
        @update:model-value="emit('toggle-auto-tts', $event)"
      />
      <div class="tool-spacer" />
      <el-button size="small" @click="emit('switch-persona')" :disabled="!sessionId || ended">
        换面试官
      </el-button>
      <el-button
        size="small"
        type="danger"
        plain
        :disabled="!sessionId || ended"
        :loading="ending"
        @click="emit('end')"
      >
        结束面试
      </el-button>
    </div>
  </footer>
</template>

<style scoped>
.input-area {
  flex-shrink: 0;
  background: #fff;
  border-top: 1px solid #ebeef5;
  padding: 10px 16px 12px;
  position: relative;
}
.ended-banner {
  text-align: center;
  color: #67c23a;
  font-size: 13px;
  font-weight: 600;
  margin-bottom: 8px;
}
.input-row {
  display: flex;
  align-items: center;
  gap: 10px;
}
.text-input {
  flex: 1;
}
.input-disabled {
  opacity: 0.5;
  pointer-events: none;
}
.tool-row {
  margin-top: 8px;
  display: flex;
  align-items: center;
  gap: 8px;
}
.tool-spacer {
  flex: 1;
}
</style>
