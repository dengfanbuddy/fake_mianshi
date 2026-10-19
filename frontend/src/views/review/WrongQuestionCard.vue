<script setup>
/**
 * 错题卡片：展示一道错题的题干、我的答案、正确答案与解析。
 *
 * 错题本和考试页相反——这里**必须**给出正确答案与解析，否则没法复盘。
 */
import { computed, ref } from 'vue'
import { renderMd, letter, optionText, parseOptions, TYPE_LABEL, TYPE_TAG } from '../written-test/examHelpers'

const props = defineProps({
  item: { type: Object, required: true },
  index: { type: Number, default: 0 },
})
const emit = defineEmits(['retry'])

const showExplanation = ref(false)

const html = computed(() => renderMd(props.item.content))
const options = computed(() => parseOptions(props.item))
/** 正确答案可能是字母串（"AB"），转成读得懂的选项文本 */
const correctText = computed(() => describeAnswer(props.item.correctAnswer, options.value))
const myText = computed(() => {
  const raw = props.item.myAnswer
  if (raw === null || raw === undefined || String(raw).trim() === '') return '（未作答）'
  return describeAnswer(raw, options.value)
})

function describeAnswer(answer, opts) {
  const raw = answer === null || answer === undefined ? '' : String(answer).trim()
  if (!raw) return '（空）'
  // 选择题：答案是大写字母串，尽量映射成「B. 选项文本」
  if (opts.length && /^[A-Z]+$/.test(raw)) {
    return raw
      .split('')
      .map((ch) => {
        const idx = ch.charCodeAt(0) - 65
        const opt = opts[idx]
        return opt === undefined ? ch : `${ch}. ${optionText(opt, idx)}`
      })
      .join('；')
  }
  return raw
}
</script>

<template>
  <div :class="['wrong-card', { corrected: item.corrected }]">
    <div class="card-head">
      <span class="idx">{{ index + 1 }}</span>
      <el-tag :type="TYPE_TAG[item.type]" size="small" effect="plain">
        {{ TYPE_LABEL[item.type] || item.type }}
      </el-tag>
      <el-tag v-if="item.corrected" type="success" size="small" effect="dark">已订正</el-tag>
      <el-tag v-else type="danger" size="small" effect="dark">待订正</el-tag>
      <span v-if="item.retryCount > 0" class="retry-hint">重练 {{ item.retryCount }} 次</span>
      <span class="spacer"></span>
      <el-button type="primary" size="small" text @click="emit('retry', item)">
        {{ item.corrected ? '再练一次' : '重练' }}
      </el-button>
    </div>

    <div v-if="item.knowledgePoints?.length" class="points">
      <el-tag v-for="p in item.knowledgePoints" :key="p" type="info" size="small" effect="plain">
        {{ p }}
      </el-tag>
    </div>

    <div class="q-content markdown-body" v-html="html"></div>

    <div class="answers">
      <div class="answer-row bad">
        <span class="label">我的答案</span>
        <span class="value">{{ myText }}</span>
      </div>
      <div class="answer-row good">
        <span class="label">正确答案</span>
        <span class="value">{{ correctText }}</span>
      </div>
    </div>

    <div v-if="item.explanation" class="explain-wrap">
      <el-button link type="primary" size="small" @click="showExplanation = !showExplanation">
        {{ showExplanation ? '收起解析' : '查看解析' }}
      </el-button>
      <div v-if="showExplanation" class="explain markdown-body" v-html="renderMd(item.explanation)"></div>
    </div>
    <div v-if="item.corrected && item.correctedAt" class="corrected-at">
      订正于 {{ item.correctedAt.replace('T', ' ').slice(0, 19) }}
    </div>
  </div>
</template>

<style scoped>
.wrong-card {
  background: #fff;
  border: 1px solid #e4e7ed;
  border-left: 3px solid #f56c6c;
  border-radius: 8px;
  padding: 16px 18px;
  margin-bottom: 14px;
}
.wrong-card.corrected {
  border-left-color: #67c23a;
}
.card-head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
}
.idx {
  width: 24px;
  height: 24px;
  border-radius: 50%;
  background: #409eff;
  color: #fff;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  flex-shrink: 0;
}
.retry-hint {
  color: #909399;
  font-size: 12px;
}
.spacer {
  flex: 1;
}
.points {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-bottom: 10px;
}
.q-content {
  font-size: 15px;
  line-height: 1.6;
  color: #303133;
  margin-bottom: 12px;
  word-break: break-word;
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
.answers {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin-bottom: 8px;
}
.answer-row {
  display: flex;
  align-items: baseline;
  gap: 10px;
  font-size: 14px;
  line-height: 1.6;
}
.answer-row .label {
  flex-shrink: 0;
  font-size: 13px;
  color: #909399;
  min-width: 62px;
}
.answer-row.bad .value {
  color: #f56c6c;
}
.answer-row.good .value {
  color: #67c23a;
  font-weight: 500;
}
.explain {
  margin-top: 8px;
  background: #f9fafb;
  border-radius: 6px;
  padding: 10px 12px;
  font-size: 14px;
  line-height: 1.7;
  color: #303133;
}
.explain :deep(p) {
  margin: 4px 0;
}
.corrected-at {
  margin-top: 8px;
  font-size: 12px;
  color: #909399;
}
</style>
