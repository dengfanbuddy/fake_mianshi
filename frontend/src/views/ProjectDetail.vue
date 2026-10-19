<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getProject, updateProject, deleteProject } from '../api/project'
import { getResume } from '../api/resume'
import { getPosition, savePosition, generatePositionFromResume } from '../api/position'
import { getSessionsByProject, deleteSession } from '../api/session'
import { getWeaknessTags } from '../api/weakness'
import ProjectHeaderCard from '../components/project/ProjectHeaderCard.vue'
import ProjectEditDialog from '../components/project/ProjectEditDialog.vue'
import ResumePanel from '../components/project/ResumePanel.vue'
import PositionPanel from '../components/project/PositionPanel.vue'
import SessionHistoryPanel from '../components/project/SessionHistoryPanel.vue'
import WeaknessPanel from '../components/project/WeaknessPanel.vue'
import HistoryAnalysisCard from '../components/project/HistoryAnalysisCard.vue'

const route = useRoute()
const router = useRouter()
const projectId = Number(route.params.id)

const deleting = ref(false)

/* ---------------- 通用加载 ---------------- */
// code === 200 时写入 target（空值兜底为 fallback），否则提示错误；异常由拦截器提示
async function loadOne(fetcher, loading, target, errorText, fallback = null) {
  loading.value = true
  try {
    const res = await fetcher()
    if (res.code === 200) {
      target.value = res.data || fallback
    } else {
      ElMessage.error(res.message || errorText)
    }
  } catch (e) {
    // 拦截器已提示
  } finally {
    loading.value = false
  }
}

/* ---------------- 项目基础信息 ---------------- */
const project = ref(null)
const projectLoading = ref(false)
const editDialogVisible = ref(false)
const savingProject = ref(false)

const fetchProject = () => loadOne(() => getProject(projectId), projectLoading, project, '加载项目失败')

const openEdit = () => { editDialogVisible.value = true }

async function handleSaveProject(payload) {
  if (!payload.name) {
    ElMessage.warning('项目名称不能为空')
    return
  }
  savingProject.value = true
  try {
    const res = await updateProject(projectId, payload)
    if (res.code === 200) {
      ElMessage.success('项目已更新')
      editDialogVisible.value = false
      await fetchProject()
    } else {
      ElMessage.error(res.message || '更新失败')
    }
  } catch (e) {
    // 拦截器已提示
  } finally {
    savingProject.value = false
  }
}

async function handleDeleteProject() {
  if (deleting.value) return
  deleting.value = true
  try {
    const res = await deleteProject(projectId)
    if (res.code === 200) {
      ElMessage.success('项目已删除')
      router.push('/')
    } else {
      ElMessage.error(res.message || '删除失败')
    }
  } catch (e) {
    // 拦截器已提示
  } finally {
    deleting.value = false
  }
}

/* ---------------- 简历 ---------------- */
const resume = ref(null)
const resumeLoading = ref(false)

const resumeAnalysis = computed(() => {
  if (!resume.value?.analysisResult) return null
  try {
    return JSON.parse(resume.value.analysisResult)
  } catch (e) {
    return null
  }
})

const fetchResume = () => loadOne(() => getResume(projectId), resumeLoading, resume, '加载简历失败')

// 子组件上传/分析完成后回传最新简历（analysisResult 变化会驱动 resumeAnalysis 重算）
const handleResumeUpdated = (data) => { resume.value = data || null }

/* ---------------- 职位需求 ---------------- */
const position = ref(null)
const positionLoading = ref(false)
const savingPosition = ref(false)
const generatingPosition = ref(false)
const positionEditing = ref(false)

const fetchPosition = () => loadOne(() => getPosition(projectId), positionLoading, position, '加载职位需求失败')

// payload 由 PositionPanel 按接口字段形状组装（含技术栈 JSON 序列化）
async function handleSavePosition(payload) {
  savingPosition.value = true
  try {
    const res = await savePosition(projectId, payload)
    if (res.code === 200) {
      ElMessage.success('职位需求已保存')
      position.value = res.data
      positionEditing.value = false
    } else {
      ElMessage.error(res.message || '保存失败')
    }
  } catch (e) {
    // 拦截器已提示
  } finally {
    savingPosition.value = false
  }
}

async function handleGenerateFromResume() {
  generatingPosition.value = true
  try {
    const res = await generatePositionFromResume(projectId)
    if (res.code === 200) {
      ElMessage.success('已从简历生成职位需求')
      position.value = res.data
      positionEditing.value = false
    } else {
      ElMessage.error(res.message || '生成失败')
    }
  } catch (e) {
    // 拦截器已提示
  } finally {
    generatingPosition.value = false
  }
}

/* ---------------- 面试历史 ---------------- */
const sessions = ref([])
const sessionsLoading = ref(false)

// 进行中的考试（可继续）
const inProgressSessions = computed(() =>
  (sessions.value || []).filter((s) => s.status === 'IN_PROGRESS')
)

const fetchSessions = () => loadOne(() => getSessionsByProject(projectId), sessionsLoading, sessions, '加载面试历史失败', [])

async function handleDeleteSession(sessionId) {
  if (deleting.value) return
  deleting.value = true
  try {
    const res = await deleteSession(sessionId)
    if (res.code === 200) {
      ElMessage.success('面试已删除（含录音）')
      await fetchSessions()
    } else {
      ElMessage.error(res.message || '删除失败')
    }
  } catch (e) {
    // 拦截器已提示
  } finally {
    deleting.value = false
  }
}

/* ---------------- 路由跳转 ---------------- */
// 继续进行中的笔试/面试
function continueSession(s) {
  if (s.type === 'WRITTEN') {
    router.push(`/written-test/exam/${s.id}`)
  } else {
    router.push(`/mock-interview/${projectId}?sessionId=${s.id}`)
  }
}

// 完整流程：先笔试再模拟面试。已有完成的笔试则直接进面试（参考笔试结果），否则先进笔试
async function startFullFlow() {
  try {
    const res = await getSessionsByProject(projectId)
    const hasWritten = (res.data || []).some(
      (s) => s.type === 'WRITTEN' && s.status === 'COMPLETED'
    )
    if (hasWritten) {
      router.push(`/mock-interview/${projectId}?refWritten=1`)
    } else {
      router.push(`/written-test/start/${projectId}?flow=full`)
    }
  } catch (e) {
    // 查询失败则按无笔试处理，先进笔试
    router.push(`/written-test/start/${projectId}?flow=full`)
  }
}

const startWrittenTest = () => router.push(`/written-test/start/${projectId}`)
const startMockInterview = () => router.push(`/mock-interview/${projectId}`)
const goHistoryAnalysis = () => router.push(`/analysis/history/${projectId}`)
const viewReport = (sessionId) =>
  router.push({ path: `/analysis/session/${sessionId}`, query: { projectId } })

/* ---------------- 弱点标签 ---------------- */
const weaknessTags = ref([])
const weaknessLoading = ref(false)

const fetchWeakness = () => loadOne(() => getWeaknessTags(projectId), weaknessLoading, weaknessTags, '加载弱点标签失败', [])

/* ---------------- 初始化 ---------------- */
onMounted(() => {
  fetchProject()
  fetchResume()
  fetchPosition()
  fetchSessions()
  fetchWeakness()
})
</script>

<template>
  <div v-loading="projectLoading" class="project-detail">
    <!-- 顶部：项目名 + 描述 + 编辑/删除 -->
    <ProjectHeaderCard :project="project" @edit="openEdit" @delete="handleDeleteProject" />

    <!-- 简历卡 -->
    <ResumePanel
      :project-id="projectId"
      :resume="resume"
      :analysis="resumeAnalysis"
      :loading="resumeLoading"
      :generating-position="generatingPosition"
      @update:resume="handleResumeUpdated"
      @generate-position="handleGenerateFromResume"
    />

    <!-- 职位需求卡 -->
    <PositionPanel
      v-model:editing="positionEditing"
      :position="position"
      :loading="positionLoading"
      :saving="savingPosition"
      :generating="generatingPosition"
      :has-resume-analysis="!!(resume && resumeAnalysis)"
      @save="handleSavePosition"
      @generate="handleGenerateFromResume"
    />

    <!-- 面试历史卡 -->
    <SessionHistoryPanel
      :sessions="sessions"
      :loading="sessionsLoading"
      :in-progress-sessions="inProgressSessions"
      @start-written="startWrittenTest"
      @start-mock="startMockInterview"
      @start-full-flow="startFullFlow"
      @continue="continueSession"
      @view-report="viewReport"
      @delete="handleDeleteSession"
    />

    <!-- 弱点标签卡 -->
    <WeaknessPanel :tags="weaknessTags" :loading="weaknessLoading" :project-id="projectId" />

    <!-- 历史分析卡 -->
    <HistoryAnalysisCard @open="goHistoryAnalysis" />

    <!-- 编辑项目弹窗 -->
    <ProjectEditDialog
      v-model:visible="editDialogVisible"
      :project="project"
      :saving="savingProject"
      @submit="handleSaveProject"
    />
  </div>
</template>

<style scoped>
.project-detail {
  max-width: 1100px;
  margin: 0 auto;
}
</style>
