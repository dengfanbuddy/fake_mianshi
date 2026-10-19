<script setup>
/**
 * 答题卡：题号网格 + 图例 + 交卷按钮。
 * 只负责展示与派发事件，不碰答案数据本身。
 */
import { computed } from 'vue'
import { isAnswered } from './examHelpers'

const props = defineProps({
  questions: { type: Array, default: () => [] },
  answers: { type: Object, default: () => ({}) },
  submitting: { type: Boolean, default: false },
})
const emit = defineEmits(['goto', 'submit'])

const answeredFlags = computed(() => props.questions.map((q) => isAnswered(q, props.answers)))
const answeredCount = computed(() => answeredFlags.value.filter(Boolean).length)
const unansweredCount = computed(() => props.questions.length - answeredCount.value)
</script>

<template>
  <div class="answer-card">
    <el-card shadow="never" class="card">
      <div class="card-title">答题卡</div>
      <div class="grid">
        <div
          v-for="(q, idx) in questions"
          :key="q.id"
          :class="['num', { answered: answeredFlags[idx] }]"
          @click="emit('goto', idx)"
        >
          {{ idx + 1 }}
        </div>
      </div>
      <div class="legend">
        <span class="legend-item"><span class="dot answered"></span>已答</span>
        <span class="legend-item"><span class="dot"></span>未答</span>
      </div>
      <el-popconfirm
        :title="unansweredCount > 0 ? `还有 ${unansweredCount} 题未答，确认要交卷吗？` : '确认要交卷吗？'"
        confirm-button-text="确认交卷"
        cancel-button-text="再检查一下"
        width="260"
        @confirm="emit('submit')"
      >
        <template #reference>
          <el-button type="primary" class="submit-btn" :loading="submitting" style="width: 100%">
            交卷
          </el-button>
        </template>
      </el-popconfirm>
    </el-card>
  </div>
</template>

<style scoped>
.answer-card {
  width: 230px;
  flex-shrink: 0;
}
.card {
  border-radius: 8px;
}
.card-title {
  font-size: 15px;
  font-weight: 600;
  margin-bottom: 12px;
}
.grid {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 8px;
  margin-bottom: 12px;
}
.num {
  aspect-ratio: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  border: 1px solid #dcdfe6;
  border-radius: 6px;
  font-size: 13px;
  color: #606266;
  cursor: pointer;
  transition: all 0.2s;
  background: #fff;
}
.num:hover {
  border-color: #409eff;
  color: #409eff;
}
.num.answered {
  background: #409eff;
  border-color: #409eff;
  color: #fff;
}
.legend {
  display: flex;
  gap: 16px;
  margin-bottom: 16px;
  font-size: 13px;
  color: #606266;
}
.legend-item {
  display: inline-flex;
  align-items: center;
  gap: 5px;
}
.dot {
  width: 12px;
  height: 12px;
  border-radius: 3px;
  border: 1px solid #dcdfe6;
  display: inline-block;
  background: #fff;
}
.dot.answered {
  background: #409eff;
  border-color: #409eff;
}
@media (max-width: 768px) {
  .answer-card {
    width: 100%;
    order: 2;
  }
}
</style>
