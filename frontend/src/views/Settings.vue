<script setup>
/**
 * 设置页外壳：只负责布局与模块编排。
 *
 * 具体功能都在 views/settings/ 下：
 * - AiModelPanel   AI 模型配置
 * - VoicePanel     语音供应商 / sidecar / 密钥 / 识别热词（含 MicSelfTest）
 * - PersonaPanel   面试官风格（表单化 styleConfig + 试听）
 *
 * 这样拆是因为原文件近 900 行，改风格表单要在语音那坨代码中间找位置，很容易误改。
 */
import AiModelPanel from './settings/AiModelPanel.vue'
import VoicePanel from './settings/VoicePanel.vue'
import PersonaPanel from './settings/PersonaPanel.vue'
</script>

<template>
  <div class="settings">
    <AiModelPanel />
    <VoicePanel />
    <PersonaPanel />
  </div>
</template>

<style scoped>
.settings {
  max-width: 1100px;
  margin: 0 auto;
}
</style>
