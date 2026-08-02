<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getProject, updateProject, deleteProject } from '../api/project'
import { getResume, uploadResume, analyzeResume } from '../api/resume'
import { getPosition, savePosition, generatePositionFromResume } from '../api/position'
import { getSessionsByProject, deleteSession } from '../api/session'
import { getWeaknessTags } from '../api/weakness'

const route = useRoute()
const router = useRouter()
const projectId = Number(route.params.id)

/* ---------------- 项目基础信息 ---------------- */
const project = ref(null)
const projectLoading = ref(false)
const editDialogVisible = ref(false)
const savingProject = ref(false)
const editForm = ref({ name: '', targetPosition: '', description: '' })

async function fetchProject() {
  projectLoading.value = true
  try {
    const res = await getProject(projectId)
    if (res.code === 200) {
      project.value = res.data
      editForm.value = {
        name: res.data?.name || '',
        description: res.data?.description || '',
      }
    } else {
      ElMessage.error(res.message || '加载项目失败')
    }
  } catch (e) {
    // 拦截器已提示
  } finally {
    projectLoading.value = false
  }
}

function openEdit() {
  editForm.value = {
    name: project.value?.name || '',
    description: project.value?.description || '',
  }
  editDialogVisible.value = true
}

async function handleSaveProject() {
  if (!editForm.value.name.trim()) {
    ElMessage.warning('项目名称不能为空')
    return
  }
  savingProject.value = true
  try {
    const res = await updateProject(projectId, {
      name: editForm.value.name.trim(),
      targetPosition: editForm.value.targetPosition.trim(),
      description: editForm.value.description.trim(),
    })
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
  }
}

/* ---------------- 简历 ---------------- */
const resume = ref(null)
const resumeLoading = ref(false)
const uploading = ref(false)
const analyzing = ref(false)
const showUpload = ref(false)

const resumeAnalysis = computed(() => {
  if (!resume.value?.analysisResult) return null
  try {
    return JSON.parse(resume.value.analysisResult)
  } catch (e) {
    return null
  }
})

async function fetchResume() {
  resumeLoading.value = true
  try {
    const res = await getResume(projectId)
    if (res.code === 200) {
      resume.value = res.data || null
    } else {
      ElMessage.error(res.message || '加载简历失败')
    }
  } catch (e) {
    // 拦截器已提示
  } finally {
    resumeLoading.value = false
  }
}

async function handleUpload(options) {
  uploading.value = true
  try {
    const res = await uploadResume(projectId, options.file)
    if (res.code === 200) {
      resume.value = res.data
      ElMessage.success('简历上传并解析成功，正在调用 AI 分析…')
      options.onSuccess && options.onSuccess(res)
      await runAnalyze(res.data.id)
    } else {
      ElMessage.error(res.message || '上传失败')
      options.onError && options.onError(res)
    }
  } catch (e) {
    options.onError && options.onError(e)
  } finally {
    uploading.value = false
  }
}

async function runAnalyze(resumeId) {
  analyzing.value = true
  try {
    const res = await analyzeResume(resumeId)
    if (res.code === 200) {
      resume.value = res.data
      ElMessage.success('简历 AI 分析完成')
    } else {
      ElMessage.error(res.message || 'AI 分析失败')
    }
  } catch (e) {
    // 拦截器已提示
  } finally {
    analyzing.value = false
  }
}

function reUpload() {
  showUpload.value = true
}

/* ---------------- 职位需求 ---------------- */
const position = ref(null)
const positionLoading = ref(false)
const savingPosition = ref(false)
const generatingPosition = ref(false)
const positionEditing = ref(false)

const seniorityOptions = ['初级', '中级', '高级']
const companyTypeOptions = ['大厂', '创业公司', '外企', '其他']

const positionForm = ref({
  id: null,
  jobTitle: '',
  seniority: '',
  companyType: '',
  experience: '',
  techStack: [],
  jobDescription: '',
})

const techStackList = computed(() => {
  if (!position.value?.techStack) return []
  try {
    const arr = JSON.parse(position.value.techStack)
    return Array.isArray(arr) ? arr : []
  } catch (e) {
    return []
  }
})

function parsePositionToForm(p) {
  let stack = []
  if (p?.techStack) {
    try {
      const arr = JSON.parse(p.techStack)
      stack = Array.isArray(arr) ? arr : []
    } catch (e) {
      stack = []
    }
  }
  positionForm.value = {
    id: p?.id ?? null,
    jobTitle: p?.jobTitle || '',
    seniority: p?.seniority || '',
    companyType: p?.companyType || '',
    experience: p?.experience || '',
    techStack: stack,
    jobDescription: p?.jobDescription || '',
  }
}

async function fetchPosition() {
  positionLoading.value = true
  try {
    const res = await getPosition(projectId)
    if (res.code === 200) {
      position.value = res.data || null
      if (position.value) parsePositionToForm(position.value)
    } else {
      ElMessage.error(res.message || '加载职位需求失败')
    }
  } catch (e) {
    // 拦截器已提示
  } finally {
    positionLoading.value = false
  }
}

function startEditPosition() {
  parsePositionToForm(position.value)
  positionEditing.value = true
}

function cancelEditPosition() {
  positionEditing.value = false
  if (position.value) parsePositionToForm(position.value)
}

async function handleSavePosition() {
  if (!positionForm.value.jobTitle.trim()) {
    ElMessage.warning('请填写岗位名称')
    return
  }
  savingPosition.value = true
  try {
    const res = await savePosition(projectId, {
      id: positionForm.value.id,
      jobTitle: positionForm.value.jobTitle.trim(),
      seniority: positionForm.value.seniority,
      companyType: positionForm.value.companyType,
      experience: positionForm.value.experience.trim(),
      techStack: JSON.stringify(positionForm.value.techStack),
      jobDescription: positionForm.value.jobDescription.trim(),
    })
    if (res.code === 200) {
      ElMessage.success('职位需求已保存')
      position.value = res.data
      positionEditing.value = false
      parsePositionToForm(position.value)
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
      parsePositionToForm(position.value)
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

// 继续进行中的笔试/面试
function continueSession(s) {
  if (s.type === 'WRITTEN') {
    router.push(`/written-test/exam/${s.id}`)
  } else {
    router.push(`/mock-interview/${projectId}?sessionId=${s.id}`)
  }
}

function typeText(type) {
  return { WRITTEN: '笔试', MOCK: '模拟面试', BOTH: '笔试+面试' }[type] || type || '—'
}

function statusText(status) {
  return { PENDING: '待开始', IN_PROGRESS: '进行中', COMPLETED: '已完成', ABANDONED: '已放弃' }[status] || status || '—'
}

function statusTagType(status) {
  return { PENDING: 'info', IN_PROGRESS: 'warning', COMPLETED: 'success', ABANDONED: 'info' }[status] || 'info'
}

function typeTagType(type) {
  return { WRITTEN: 'primary', MOCK: 'success', BOTH: 'warning' }[type] || 'info'
}

function formatTime(s) {
  if (!s) return '—'
  return new Date(s).toLocaleString()
}

async function fetchSessions() {
  sessionsLoading.value = true
  try {
    const res = await getSessionsByProject(projectId)
    if (res.code === 200) {
      sessions.value = res.data || []
    } else {
      ElMessage.error(res.message || '加载面试历史失败')
    }
  } catch (e) {
    // 拦截器已提示
  } finally {
    sessionsLoading.value = false
  }
}

async function handleDeleteSession(sessionId) {
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

function viewReport(sessionId) {
  router.push({ path: `/analysis/session/${sessionId}`, query: { projectId } })
}

/* ---------------- 弱点标签 ---------------- */
const weaknessTags = ref([])
const weaknessLoading = ref(false)

const masteryTagMap = {
  WEAK: { type: 'danger', label: '薄弱' },
  FAIR: { type: 'warning', label: '一般' },
  GOOD: { type: 'success', label: '良好' },
  STRONG: { type: 'primary', label: '优秀' },
}

function masteryMeta(level) {
  return masteryTagMap[level] || { type: 'info', label: level || '未知' }
}

async function fetchWeakness() {
  weaknessLoading.value = true
  try {
    const res = await getWeaknessTags(projectId)
    if (res.code === 200) {
      weaknessTags.value = res.data || []
    } else {
      ElMessage.error(res.message || '加载弱点标签失败')
    }
  } catch (e) {
    // 拦截器已提示
  } finally {
    weaknessLoading.value = false
  }
}

function goHistoryAnalysis() {
  router.push(`/analysis/history/${projectId}`)
}

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
    <div class="page-header">
      <div class="header-left">
        <h2 class="project-name">{{ project?.name || '项目详情' }}</h2>
        <p class="project-desc">{{ project?.description || '暂无描述' }}</p>
      </div>
      <div class="header-actions">
        <el-button @click="openEdit">编辑</el-button>
        <el-popconfirm title="确定删除该项目吗？删除后关联数据将一并清除" confirm-button-text="删除" cancel-button-text="取消" @confirm="handleDeleteProject">
          <template #reference>
            <el-button type="danger" plain>删除</el-button>
          </template>
        </el-popconfirm>
      </div>
    </div>

    <!-- 简历卡 -->
    <el-card class="section" shadow="never">
      <template #header><span>简历</span></template>

      <div v-loading="resumeLoading" class="section-body">
        <!-- 已上传：文件名 + 重新上传 + AI 分析 -->
        <template v-if="resume && !showUpload">
          <div class="resume-info">
            <el-icon class="file-icon">📄</el-icon>
            <span class="filename">{{ resume.originalFilename }}</span>
            <el-button type="primary" link @click="reUpload">重新上传</el-button>
          </div>
          <div class="resume-actions">
            <el-button
              type="primary"
              :loading="analyzing"
              @click="runAnalyze(resume.id)"
            >
              {{ analyzing ? 'AI 分析中，约需10-30秒…' : 'AI 分析简历' }}
            </el-button>
            <el-button v-if="resumeAnalysis" type="warning" plain :loading="generatingPosition" @click="handleGenerateFromResume">
              从简历生成职位需求
            </el-button>
          </div>
        </template>

        <!-- 未上传 / 重新上传状态 -->
        <el-upload
          v-else
          drag
          action="#"
          accept=".pdf"
          :show-file-list="false"
          :http-request="handleUpload"
          :disabled="uploading || analyzing"
        >
          <div class="upload-inner">
            <div class="upload-icon">⬆️</div>
            <div class="upload-text">将简历 PDF 拖到此处，或<em>点击上传</em></div>
            <div class="upload-hint">支持 .pdf 格式；上传成功后自动解析并调用 AI 分析</div>
          </div>
        </el-upload>
        <div v-if="resume && showUpload" class="reupload-cancel">
          <el-button type="primary" link @click="showUpload = false">取消重新上传</el-button>
        </div>

        <!-- AI 分析结果 -->
        <div v-if="resumeAnalysis" class="analysis-result">
          <el-divider content-position="left">简历 AI 分析结果</el-divider>
          <div class="analysis-grid">
            <div class="analysis-item">
              <span class="label">经验年限</span>
              <span class="value">{{ resumeAnalysis.experienceYears ?? '—' }}</span>
            </div>
            <div class="analysis-item">
              <span class="label">当前职位</span>
              <span class="value">{{ resumeAnalysis.currentPosition || '—' }}</span>
            </div>
            <div class="analysis-item">
              <span class="label">建议岗位</span>
              <span class="value highlight">{{ resumeAnalysis.suggestedPosition || '—' }}</span>
            </div>
            <div class="analysis-item">
              <span class="label">建议职级</span>
              <span class="value">{{ resumeAnalysis.suggestedSeniority || '—' }}</span>
            </div>
          </div>

          <div class="analysis-block">
            <div class="block-label">技术栈</div>
            <div class="tag-list">
              <el-tag
                v-for="(t, i) in (resumeAnalysis.techStack || [])"
                :key="i"
                size="small"
                class="tag-item"
              >{{ t }}</el-tag>
              <span v-if="!(resumeAnalysis.techStack || []).length" class="muted">—</span>
            </div>
          </div>

          <div class="analysis-block">
            <div class="block-label">项目经验</div>
            <ul v-if="(resumeAnalysis.projects || []).length" class="project-list">
              <li v-for="(p, i) in resumeAnalysis.projects" :key="i">{{ p }}</li>
            </ul>
            <span v-else class="muted">—</span>
          </div>

          <div v-if="resumeAnalysis.education" class="analysis-block">
            <div class="block-label">教育背景</div>
            <div>{{ resumeAnalysis.education }}</div>
          </div>

          <div v-if="resumeAnalysis" class="confirm-tip">
            <el-alert
              type="info"
              :closable="false"
              show-icon
              title="确认分析结果符合你的求职方向后，可一键生成目标岗位需求，用于后续笔试与模拟面试的题目定制。"
            />
          </div>
        </div>

        <el-empty
          v-else-if="!resumeLoading && resume && !showUpload"
          description="尚未进行 AI 分析，点击上方按钮生成简历分析"
        />
      </div>
    </el-card>

    <!-- 职位需求卡 -->
    <el-card class="section" shadow="never">
      <template #header><span>职位需求</span></template>

      <div v-loading="positionLoading" class="section-body">
        <!-- 已有职位：展示 -->
        <template v-if="position && !positionEditing">
          <div class="position-summary">
            <div class="pos-title">
              <span class="job-title">{{ position.jobTitle }}</span>
              <el-tag size="small" type="info" effect="plain">{{ position.source === 'RESUME' ? '来自简历' : '手动填写' }}</el-tag>
              <el-button class="edit-btn" type="primary" link @click="startEditPosition">编辑</el-button>
            </div>
            <div class="pos-meta">
              <el-tag v-if="position.seniority" size="small">{{ position.seniority }}</el-tag>
              <el-tag v-if="position.companyType" size="small" type="success">{{ position.companyType }}</el-tag>
            </div>
            <div v-if="position.experience" class="pos-line"><span class="label">经验要求：</span>{{ position.experience }}</div>
            <div class="pos-line"><span class="label">技术栈：</span>
              <template v-if="techStackList.length">
                <el-tag v-for="(t, i) in techStackList" :key="i" size="small" class="tag-item">{{ t }}</el-tag>
              </template>
              <span v-else class="muted">—</span>
            </div>
            <div v-if="position.jobDescription" class="pos-line">
              <span class="label">岗位职责：</span>
              <span class="job-desc">{{ position.jobDescription }}</span>
            </div>
          </div>
        </template>

        <!-- 编辑 / 手动填写表单 -->
        <template v-else>
          <el-form label-width="90px" class="position-form">
            <el-form-item label="岗位名称" required>
              <el-input v-model="positionForm.jobTitle" placeholder="例如：后端开发工程师 / 产品经理" />
            </el-form-item>
            <el-form-item label="职级">
              <el-select v-model="positionForm.seniority" placeholder="选择职级" clearable class="form-select">
                <el-option v-for="s in seniorityOptions" :key="s" :label="s" :value="s" />
              </el-select>
            </el-form-item>
            <el-form-item label="公司类型">
              <el-select v-model="positionForm.companyType" placeholder="选择公司类型" clearable class="form-select">
                <el-option v-for="c in companyTypeOptions" :key="c" :label="c" :value="c" />
              </el-select>
            </el-form-item>
            <el-form-item label="经验要求">
              <el-input v-model="positionForm.experience" placeholder="例如：3-5 年相关经验" />
            </el-form-item>
            <el-form-item label="技术栈">
              <el-select
                v-model="positionForm.techStack"
                multiple
                filterable
                allow-create
                default-first-option
                :reserve-keyword="false"
                placeholder="输入后回车添加，例如：数据库、项目架构"
                class="form-select"
              >
                <el-option v-for="t in positionForm.techStack" :key="t" :label="t" :value="t" />
              </el-select>
            </el-form-item>
            <el-form-item label="岗位职责">
              <el-input
                v-model="positionForm.jobDescription"
                type="textarea"
                :rows="4"
                placeholder="描述岗位职责与能力要求"
              />
            </el-form-item>
          </el-form>
          <div class="form-actions">
            <el-button v-if="positionEditing" @click="cancelEditPosition">取消</el-button>
            <el-button type="primary" :loading="savingPosition" @click="handleSavePosition">保存</el-button>
            <el-button
              v-if="resume && resumeAnalysis"
              type="warning"
              plain
              :loading="generatingPosition"
              @click="handleGenerateFromResume"
            >从简历生成</el-button>
          </div>
        </template>
      </div>
    </el-card>

    <!-- 面试历史卡 -->
    <el-card class="section" shadow="never">
      <template #header><span>面试历史</span></template>

      <div class="section-body">
        <div class="start-buttons">
          <el-button type="primary" @click="router.push(`/written-test/start/${projectId}`)">📝 笔试</el-button>
          <el-button type="success" @click="router.push(`/mock-interview/${projectId}`)">🗣️ 模拟面试</el-button>
          <el-button type="warning" @click="startFullFlow">🚀 完整流程</el-button>
        </div>

        <!-- 进行中的考试：可继续 -->
        <div v-if="inProgressSessions.length" class="resume-bar">
          <div class="resume-title">⏳ 进行中的考试</div>
          <div v-for="s in inProgressSessions" :key="s.id" class="resume-item">
            <el-tag :type="typeTagType(s.type)" size="small" effect="dark">
              {{ s.type === 'WRITTEN' ? '笔试' : '模拟面试' }}
            </el-tag>
            <span class="resume-info">已开始于 {{ formatTime(s.startedAt) }}</span>
            <el-button type="primary" size="small" plain @click="continueSession(s)">
              {{ s.type === 'WRITTEN' ? '继续笔试' : '继续面试' }}
            </el-button>
          </div>
        </div>

        <el-table
          v-if="sessions.length"
          v-loading="sessionsLoading"
          :data="sessions"
          border
          stripe
          class="session-table"
        >
          <el-table-column label="类型" width="130">
            <template #default="{ row }">
              <el-tag :type="typeTagType(row.type)" size="small" effect="dark">{{ typeText(row.type) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="120">
            <template #default="{ row }">
              <el-tag :type="statusTagType(row.status)" size="small">{{ statusText(row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="时长限制" width="110">
            <template #default="{ row }">
              <span v-if="row.timeLimit">{{ row.timeLimit }} 分钟</span>
              <span v-else class="muted">—</span>
            </template>
          </el-table-column>
          <el-table-column label="开始时间" min-width="170">
            <template #default="{ row }">{{ formatTime(row.startedAt || row.createdAt) }}</template>
          </el-table-column>
          <el-table-column label="完成时间" min-width="170">
            <template #default="{ row }">{{ formatTime(row.completedAt) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="160">
            <template #default="{ row }">
              <el-button type="primary" link @click="viewReport(row.id)">查看报告</el-button>
              <el-popconfirm
                title="删除该面试及其录音？"
                confirm-button-text="删除"
                cancel-button-text="取消"
                @confirm="handleDeleteSession(row.id)"
              >
                <template #reference>
                  <el-button type="danger" link>删除</el-button>
                </template>
              </el-popconfirm>
            </template>
          </el-table-column>
        </el-table>

        <el-empty v-if="!sessionsLoading && !sessions.length" description="暂无面试记录，点击上方按钮开始第一次面试" />
      </div>
    </el-card>

    <!-- 弱点标签卡 -->
    <el-card class="section" shadow="never">
      <template #header><span>薄弱知识点</span></template>

      <div v-loading="weaknessLoading" class="section-body">
        <div v-if="weaknessTags.length" class="weakness-list">
          <div v-for="tag in weaknessTags" :key="tag.id" class="weakness-item">
            <span class="weak-point">{{ tag.knowledgePoint }}</span>
            <el-tag :type="masteryMeta(tag.masteryLevel).type" size="small" effect="dark">
              {{ masteryMeta(tag.masteryLevel).label }}
            </el-tag>
            <el-badge
              :value="tag.occurrenceCount"
              :max="99"
              class="occurrence-badge"
            ><span class="occurrence-bg">出现次数</span></el-badge>
          </div>
        </div>
        <el-empty v-else-if="!weaknessLoading" description="暂无弱点标签，完成面试分析后生成" />
      </div>
    </el-card>

    <!-- 历史分析卡 -->
    <el-card class="section" shadow="never">
      <template #header><span>综合分析</span></template>
      <div class="section-body history-analysis">
        <p class="muted">查看本项目的整体成长分析与各轮面试对比。</p>
        <el-button type="primary" @click="goHistoryAnalysis">查看历史综合分析</el-button>
      </div>
    </el-card>

    <!-- 编辑项目弹窗 -->
    <el-dialog v-model="editDialogVisible" title="编辑项目" width="480px">
      <el-form label-width="80px">
        <el-form-item label="项目名称" required>
          <el-input v-model="editForm.name" maxlength="100" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="editForm.description" type="textarea" :rows="3" maxlength="500" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="savingProject" @click="handleSaveProject">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.project-detail {
  max-width: 1100px;
  margin: 0 auto;
}
.page-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  margin-bottom: 20px;
}
.header-left {
  flex: 1;
  min-width: 0;
}
.project-name {
  margin: 0 0 6px;
  font-size: 22px;
}
.project-desc {
  margin: 0;
  color: #606266;
  font-size: 14px;
}
.header-actions {
  flex-shrink: 0;
  display: flex;
  gap: 8px;
}
.section {
  margin-bottom: 20px;
}
.resume-bar {
  background: #fdf6ec;
  border: 1px solid #f3d19e;
  border-radius: 8px;
  padding: 12px 16px;
  margin-bottom: 14px;
}
.resume-title {
  font-size: 13px;
  font-weight: 600;
  color: #b88230;
  margin-bottom: 8px;
}
.resume-item {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 8px;
}
.resume-item:last-child {
  margin-bottom: 0;
}
.resume-bar .resume-info {
  font-size: 13px;
  color: #606266;
  flex: 1;
}

/* 简历 */
.resume-info {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 14px;
}
.file-icon {
  font-size: 20px;
}
.filename {
  font-size: 15px;
  font-weight: 600;
}
.resume-actions {
  display: flex;
  gap: 12px;
}
.upload-inner {
  padding: 10px 0;
}
.upload-icon {
  font-size: 40px;
  margin-bottom: 8px;
}
.upload-text {
  font-size: 14px;
  color: #606266;
}
.upload-text em {
  color: #409eff;
  font-style: normal;
}
.upload-hint {
  font-size: 12px;
  color: #909399;
  margin-top: 6px;
}
.reupload-cancel {
  margin-top: 8px;
}
.analysis-result {
  margin-top: 16px;
}
.analysis-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
  gap: 12px;
  margin-bottom: 16px;
}
.analysis-item {
  background: #f9fafb;
  border-radius: 8px;
  padding: 12px 14px;
}
.analysis-item .label {
  display: block;
  font-size: 12px;
  color: #909399;
  margin-bottom: 4px;
}
.analysis-item .value {
  font-size: 15px;
  font-weight: 600;
  color: #303133;
}
.analysis-item .value.highlight {
  color: #409eff;
}
.analysis-block {
  margin-bottom: 12px;
}
.block-label {
  font-size: 13px;
  font-weight: 600;
  color: #606266;
  margin-bottom: 6px;
}
.tag-list {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
.tag-item {
  margin-right: 4px;
}
.project-list {
  margin: 0;
  padding-left: 20px;
  line-height: 1.8;
  color: #303133;
}
.confirm-tip {
  margin-top: 8px;
}
.muted {
  color: #c0c4cc;
}

/* 职位 */
.position-summary {
  line-height: 1.9;
}
.pos-title {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 6px;
}
.job-title {
  font-size: 18px;
  font-weight: 700;
}
.edit-btn {
  margin-left: auto;
}
.pos-meta {
  display: flex;
  gap: 8px;
  margin-bottom: 6px;
}
.pos-line {
  font-size: 14px;
  color: #303133;
  margin-bottom: 4px;
}
.pos-line .label {
  color: #909399;
}
.job-desc {
  white-space: pre-wrap;
  word-break: break-word;
}
.form-select {
  width: 100%;
}
.form-actions {
  display: flex;
  gap: 12px;
  justify-content: flex-end;
}

/* 面试历史 */
.start-buttons {
  display: flex;
  gap: 12px;
  margin-bottom: 16px;
}
.session-table {
  margin-top: 8px;
}

/* 弱点 */
.weakness-list {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
}
.weakness-item {
  display: flex;
  align-items: center;
  gap: 8px;
  background: #f9fafb;
  border-radius: 8px;
  padding: 10px 14px;
}
.weak-point {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
}
.occurrence-badge {
  margin-left: 4px;
}
.occurrence-bg {
  display: inline-block;
  font-size: 12px;
  color: #909399;
}

/* 历史分析 */
.history-analysis p {
  margin: 0 0 12px;
}
</style>
