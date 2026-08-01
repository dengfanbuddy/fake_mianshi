<script setup>
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { startTest } from '../../api/written-test'
import { useWrittenTestStore } from '../../stores/writtenTest'

const route = useRoute()
const router = useRouter()
const store = useWrittenTestStore()
const projectId = route.params.projectId

const timeLimit = ref(60)
const questionCount = ref(12)
const loading = ref(false)

async function handleStart() {
  loading.value = true
  try {
    const res = await startTest(projectId, {
      timeLimit: timeLimit.value,
      questionCount: questionCount.value,
    })
    if (res.code === 200 && res.data?.sessionId) {
      store.save({
        sessionId: res.data.sessionId,
        projectId,
        timeLimit: res.data.timeLimit,
        deadline: Date.now() + res.data.timeLimit * 60000,
        questions: res.data.questions || [],
      })
      ElMessage.success('考试开始，祝你好运！')
      router.push(`/written-test/exam/${res.data.sessionId}`)
    } else {
      ElMessage.error(res.message || '开始考试失败')
    }
  } catch (e) {
    // request 拦截器已提示错误
  } finally {
    loading.value = false
  }
}

function goBack() {
  router.back()
}
</script>

<template>
  <div class="start-page">
    <div class="start-card">
      <h2 class="title">AI 笔试考试</h2>
      <p class="subtitle">项目 ID：<el-tag type="info">{{ projectId }}</el-tag></p>

      <el-form label-width="100px" label-position="left" class="config-form">
        <el-form-item label="考试时长">
          <el-input-number v-model="timeLimit" :min="10" :max="180" :step="5" />
          <span class="unit">分钟</span>
        </el-form-item>
        <el-form-item label="题目数量">
          <el-input-number v-model="questionCount" :min="5" :max="30" :step="1" />
          <span class="unit">道</span>
        </el-form-item>
      </el-form>

      <p class="tip">包含单选题、多选题、填空题与简答题，请合理安排答题时间。</p>

      <div class="actions">
        <el-button @click="goBack">返回上一页</el-button>
        <el-button type="primary" :loading="loading" @click="handleStart">开始考试</el-button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.start-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #f5f7fa;
  padding: 24px;
}
.start-card {
  width: 460px;
  background: #fff;
  border-radius: 12px;
  padding: 40px;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.08);
}
.title {
  margin: 0 0 8px;
  font-size: 22px;
  text-align: center;
}
.subtitle {
  margin: 0 0 24px;
  text-align: center;
  color: #606266;
  font-size: 14px;
}
.config-form {
  margin-bottom: 8px;
}
.unit {
  margin-left: 8px;
  color: #909399;
  font-size: 14px;
}
.tip {
  margin: 0 0 24px;
  font-size: 12px;
  color: #909399;
}
.actions {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}
</style>
