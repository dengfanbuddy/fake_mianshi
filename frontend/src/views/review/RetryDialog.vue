<script setup>
/**
 * 错题重练弹窗：作答 → 立刻判分 → 显示正确答案与解析。
 *
 * 判分复用后端同一套规则（与交卷时完全一致），简答题则明确告知「不自动判分」，
 * 直接给参考答案让用户自评——比假装判分诚实。
 */
import { computed, ref, watch } from 'vue'
import { retryWrongQuestion } from '../../api/review'
import QuestionItem from '../written-test/QuestionItem.vue'
import { renderMd } from '../written-test/examHelpers'

const props = defineProps({
  visible: { type: Boolean, default: false },
  item: { type: Object, default: null },
})
const emit = defineEmits(['update:visible', 'done'])

const answer = ref('')
const submitting = ref(false)
const result = ref(null)

const open = computed({
  get: () => props.visible,
  set: (v) => emit('update:visible', v),
})

/** 复用考试页的单题组件：只需把 id/type/content/options 喂进去 */
const question = computed(() => {
  const it = props.item
  if (!it) return null
  return { id: it.questionId, type: it.type, content: it.content, options: it.options }
})

const isMultiple = computed(() => props.item?.type === 'MULTIPLE_CHOICE')

watch(
  () => props.visible,
  (v) => {
    if (v) {
      answer.value = isMultiple.value ? [] : ''
      result.value = null
    }
  }
)

async function submit() {
  if (!props.item || submitting.value) return
  const payload = Array.isArray(answer.value)
    ? [...answer.value].sort().join('')
    : String(answer.value ?? '').trim()
  if (!payload && props.item.autoGradable) {
    ElMessage.warning('请先作答')
    return
  }
  submitting.value = true
  try {
    const res = await retryWrongQuestion(props.item.questionId, payload)
    if (res.code === 200) {
      result.value = res.data
      emit('done', res.data)
    } else {
      ElMessage.error(res.message || '提交失败')
    }
  } catch (e) {
    // 拦截器已提示
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <el-dialog v-model="open" title="重练错题" width="680px">
    <template v-if="question">
      <QuestionItem :question="question" :index="0" v-model="answer" />

      <div v-if="result" class="result-block">
        <el-alert
          v-if="!result.autoGradable"
          type="info"
          :closable="false"
          style="margin-bottom: 12px"
        >
          简答题无法自动判分，请对照下面的参考答案与解析自评。
        </el-alert>
        <el-alert
          v-else
          :type="result.correct ? 'success' : 'error'"
          :closable="false"
          :title="result.correct ? (result.firstCorrected ? '答对了，已记入订正' : '答对了') : '还是不对，看看正确答案和解析'"
          style="margin-bottom: 12px"
        />
        <div class="ref-row">
          <span class="ref-label">正确答案</span>
          <span class="ref-value">{{ result.correctAnswer || '（无）' }}</span>
        </div>
        <div v-if="result.explanation" class="ref-explain">
          <div class="ref-label">解析</div>
          <div class="markdown-body" v-html="renderMd(result.explanation)"></div>
        </div>
        <div class="ref-meta">
          本次是第 {{ result.retryCount }} 次重练<template v-if="result.correctedAt">
            · 订正于 {{ result.correctedAt.replace('T', ' ').slice(0, 19) }}</template>
        </div>
      </div>
    </template>
    <template #footer>
      <el-button @click="open = false">关闭</el-button>
      <el-button type="primary" :loading="submitting" @click="submit">
        {{ result ? '再试一次' : '提交' }}
      </el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.result-block {
  margin-top: 16px;
  border-top: 1px dashed #e4e7ed;
  padding-top: 14px;
}
.ref-row {
  display: flex;
  align-items: baseline;
  gap: 10px;
  margin-bottom: 10px;
}
.ref-label {
  flex-shrink: 0;
  font-size: 13px;
  color: #909399;
  min-width: 62px;
}
.ref-value {
  color: #67c23a;
  font-weight: 500;
  font-size: 14px;
}
.ref-explain {
  background: #f9fafb;
  border-radius: 6px;
  padding: 10px 12px;
  font-size: 14px;
  line-height: 1.7;
  color: #303133;
  margin-bottom: 10px;
}
.ref-explain :deep(p) {
  margin: 4px 0;
}
.ref-meta {
  font-size: 12px;
  color: #909399;
}
</style>
