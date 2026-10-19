<script setup>
/**
 * 错题本 / 薄弱点复习闭环。
 *
 * 数据来源全部是已有的东西：笔试作答记录（真做错过的题）+ 分析报告产出的弱点标签。
 * 不调 LLM —— 复习是高频动作，每次都等模型生成会让人放弃使用。
 *
 * 闭环：分析报告标出薄弱点 → 这里按知识点筛错题 → 重练 → 订正后掌握程度上调。
 */
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getProjects } from '../../api/project'
import { getReviewOverview, getWrongQuestions, getWeaknessTags } from '../../api/review'
import WrongQuestionCard from './WrongQuestionCard.vue'
import RetryDialog from './RetryDialog.vue'

const route = useRoute()
const router = useRouter()

/** 记住上次查看的项目，从侧边栏进来时直接回到它 */
const LAST_PROJECT_KEY = 'review:lastProjectId'

const projects = ref([])
const projectId = ref(null)
const overview = ref(null)
const wrongList = ref([])
const weaknessTags = ref([])
const loading = ref(false)
const statusFilter = ref('all')
const pointFilter = ref('')
const retryVisible = ref(false)
const retrying = ref(null)

const statusOptions = [
  { value: 'all', label: '全部' },
  { value: 'pending', label: '待订正' },
  { value: 'corrected', label: '已订正' },
]

const masteryMeta = (level) =>
  ({
    WEAK: { type: 'danger', label: '薄弱' },
    FAIR: { type: 'warning', label: '一般' },
    GOOD: { type: 'success', label: '良好' },
    STRONG: { type: 'primary', label: '优秀' },
  })[level] || { type: 'info', label: level || '未知' }

const currentProjectName = computed(
  () => projects.value.find((p) => p.id === projectId.value)?.name || ''
)

const wrongTotalText = computed(() => overview.value?.wrongTotal ?? 0)
const progressPercent = computed(() => {
  const o = overview.value
  if (!o || !o.wrongTotal) return 0
  return Math.round((o.correctedTotal / o.wrongTotal) * 100)
})

/** 空列表文案要区分「没筛到」和「真没有」 */
const wrongListDescription = computed(() => {
  if (pointFilter.value) return `「${pointFilter.value}」下还没有错题记录`
  if (statusFilter.value === 'pending') return '没有待订正的错题，保持住'
  if (statusFilter.value === 'corrected') return '还没有订正过的错题'
  return '这个项目还没有错题记录，先去做一次笔试吧'
})

async function fetchProjects() {
  try {
    const res = await getProjects()
    if (res.code === 200) projects.value = res.data || []
  } catch (e) {
    // 拦截器已提示
  }
}

async function fetchAll() {
  if (!projectId.value) return
  loading.value = true
  try {
    const [ov, wrongs, tags] = await Promise.all([
      getReviewOverview(projectId.value).catch(() => null),
      getWrongQuestions(projectId.value, {
        status: statusFilter.value,
        knowledgePoint: pointFilter.value,
      }).catch(() => null),
      getWeaknessTags(projectId.value).catch(() => null),
    ])
    if (ov?.code === 200) overview.value = ov.data
    if (wrongs?.code === 200) wrongList.value = wrongs.data || []
    if (tags?.code === 200) weaknessTags.value = tags.data || []
  } finally {
    loading.value = false
  }
}

function selectProject(id) {
  projectId.value = id
  pointFilter.value = ''
  statusFilter.value = 'all'
  try {
    localStorage.setItem(LAST_PROJECT_KEY, String(id))
  } catch (e) {
    // 忽略：存不了下次回落到第一个项目
  }
  router.replace({ path: `/review/${id}` })
  fetchAll()
}

function togglePoint(point) {
  pointFilter.value = pointFilter.value === point ? '' : point
}

function openRetry(item) {
  retrying.value = item
  retryVisible.value = true
}

/** 重练判对后刷新列表与统计（掌握程度可能已上调） */
function onRetryDone(result) {
  if (result?.correct) {
    fetchAll()
  }
}

watch(statusFilter, fetchAll)

onMounted(async () => {
  await fetchProjects()
  const fromRoute = Number(route.params.projectId)
  const ids = projects.value.map((p) => p.id)
  if (Number.isFinite(fromRoute) && fromRoute > 0) {
    projectId.value = fromRoute
  } else {
    // 没带项目时优先回到上次看的项目：错题本是反复来的页面，
    // 每次都跳到「最新创建的项目」很烦人（那往往不是你在复习的那个）
    let remembered = null
    try {
      remembered = Number(localStorage.getItem(LAST_PROJECT_KEY))
    } catch (e) {
      remembered = null
    }
    projectId.value = ids.includes(remembered) ? remembered : (ids[0] ?? null)
  }
  if (!projectId.value) {
    ElMessage.warning('还没有面试项目，先创建一个项目再来复习')
    return
  }
  if (route.params.projectId !== String(projectId.value)) {
    router.replace({ path: `/review/${projectId.value}` })
  }
  await fetchAll()
})
</script>

<template>
  <div class="review-page">
    <header class="page-head">
      <div class="head-left">
        <h2>错题本</h2>
        <span class="sub">把真做错过的题重做一遍，订正后掌握程度会自动上调</span>
      </div>
      <el-select
        :model-value="projectId"
        placeholder="选择项目"
        style="width: 240px"
        @update:model-value="selectProject"
      >
        <el-option v-for="p in projects" :key="p.id" :label="p.name" :value="p.id" />
      </el-select>
    </header>

    <!-- 统计 -->
    <div class="stats">
      <div class="stat">
        <div class="stat-value">{{ wrongTotalText }}</div>
        <div class="stat-label">错题总数</div>
      </div>
      <div class="stat">
        <div class="stat-value pending">{{ overview?.pendingTotal ?? 0 }}</div>
        <div class="stat-label">待订正</div>
      </div>
      <div class="stat">
        <div class="stat-value corrected">{{ overview?.correctedTotal ?? 0 }}</div>
        <div class="stat-label">已订正</div>
      </div>
      <div class="stat">
        <div class="stat-value">{{ overview?.knowledgePointCount ?? 0 }}</div>
        <div class="stat-label">涉及知识点</div>
      </div>
      <div class="stat wide">
        <el-progress :percentage="progressPercent" :stroke-width="10" />
        <div class="stat-label">订正进度</div>
      </div>
    </div>

    <div class="body">
      <!-- 左：薄弱知识点 -->
      <aside class="side">
        <el-card shadow="never">
          <template #header>
            <div class="side-head">
              <span>薄弱知识点</span>
              <el-button v-if="pointFilter" link type="primary" size="small" @click="pointFilter = ''">
                清除筛选
              </el-button>
            </div>
          </template>
          <div v-if="weaknessTags.length" class="weak-list">
            <div
              v-for="tag in weaknessTags"
              :key="tag.id"
              :class="['weak-item', { active: pointFilter === tag.knowledgePoint }]"
              @click="togglePoint(tag.knowledgePoint)"
            >
              <span class="weak-name">{{ tag.knowledgePoint }}</span>
              <el-tag :type="masteryMeta(tag.masteryLevel).type" size="small" effect="dark">
                {{ masteryMeta(tag.masteryLevel).label }}
              </el-tag>
            </div>
          </div>
          <el-empty v-else description="暂无薄弱知识点（完成一次面试分析后生成）" :image-size="60" />
          <div class="side-hint">点击知识点可筛选与之相关的错题</div>
        </el-card>
      </aside>

      <!-- 右：错题列表 -->
      <main class="main">
        <div class="toolbar">
          <el-radio-group v-model="statusFilter" size="small">
            <el-radio-button v-for="o in statusOptions" :key="o.value" :value="o.value">
              {{ o.label }}
            </el-radio-button>
          </el-radio-group>
          <span v-if="pointFilter" class="filter-hint">
            正在看「{{ pointFilter }}」相关错题
          </span>
          <span class="spacer"></span>
          <span class="count-hint">{{ wrongList.length }} 道</span>
        </div>

        <div v-loading="loading" class="list-wrap">
          <WrongQuestionCard
            v-for="(item, idx) in wrongList"
            :key="item.answerId"
            :item="item"
            :index="idx"
            @retry="openRetry"
          />
          <el-empty
            v-if="!loading && !wrongList.length"
            :description="wrongListDescription"
          />
        </div>
      </main>
    </div>

    <RetryDialog v-model:visible="retryVisible" :item="retrying" @done="onRetryDone" />
  </div>
</template>

<style scoped>
.review-page {
  max-width: 1120px;
  margin: 0 auto;
}
.page-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;
}
.head-left h2 {
  margin: 0 0 4px;
  font-size: 20px;
}
.sub {
  color: #909399;
  font-size: 13px;
}
.stats {
  display: flex;
  gap: 12px;
  margin-bottom: 16px;
}
.stat {
  flex: 1;
  background: #fff;
  border: 1px solid #e4e7ed;
  border-radius: 8px;
  padding: 14px 16px;
  text-align: center;
}
.stat.wide {
  flex: 1.6;
}
.stat-value {
  font-size: 24px;
  font-weight: 600;
  color: #303133;
  font-variant-numeric: tabular-nums;
}
.stat-value.pending {
  color: #f56c6c;
}
.stat-value.corrected {
  color: #67c23a;
}
.stat-label {
  margin-top: 4px;
  font-size: 13px;
  color: #909399;
}
.body {
  display: flex;
  gap: 16px;
  align-items: flex-start;
}
.side {
  width: 260px;
  flex-shrink: 0;
}
.side-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.weak-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  max-height: 520px;
  overflow: auto;
}
.weak-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 8px 10px;
  border-radius: 6px;
  background: #f9fafb;
  cursor: pointer;
  transition: all 0.2s;
}
.weak-item:hover {
  background: #eef5ff;
}
.weak-item.active {
  background: #e6f0ff;
  box-shadow: inset 0 0 0 1px #409eff;
}
.weak-name {
  font-size: 13px;
  color: #303133;
  line-height: 1.4;
  word-break: break-word;
}
.side-hint {
  margin-top: 10px;
  font-size: 12px;
  color: #a8abb2;
}
.main {
  flex: 1;
  min-width: 0;
}
.toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
}
.spacer {
  flex: 1;
}
.filter-hint,
.count-hint {
  font-size: 13px;
  color: #909399;
}
.list-wrap {
  min-height: 160px;
}
@media (max-width: 900px) {
  .body {
    flex-direction: column;
  }
  .side {
    width: 100%;
  }
  .stats {
    flex-wrap: wrap;
  }
}
</style>
