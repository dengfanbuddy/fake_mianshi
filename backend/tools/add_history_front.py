import io

# 1. API
p = 'src/api/analysis.js'
src = io.open(p, encoding='utf-8').read()
old = '''// 历史综合分析报告
export const getHistoryAnalysis = (projectId) => request.get(`/analysis/history/${projectId}`)'''
new = '''// 历史综合分析报告
export const getHistoryAnalysis = (projectId) => request.get(`/analysis/history/${projectId}`)

// 历史综合分析状态（未分析会话数 / 是否有新分析可重新综合）
export const getHistoryStatus = (projectId) => request.get(`/analysis/history/status/${projectId}`)

// 强制重新生成历史综合分析
export const refreshHistoryAnalysis = (projectId) => request.post(`/analysis/history/refresh/${projectId}`)'''
if old in src:
    src = src.replace(old, new, 1)
    io.open(p, 'w', encoding='utf-8', newline='').write(src)
    print('api patched')
else:
    print('[WARN] api anchor not found')

# 2. HistoryReport.vue
p = 'src/views/analysis/HistoryReport.vue'
src = io.open(p, encoding='utf-8').read()

src = src.replace("import { getHistoryAnalysis } from '../../api/analysis'",
                  "import { getHistoryAnalysis, getHistoryStatus, refreshHistoryAnalysis } from '../../api/analysis'")

src = src.replace('''const loading = ref(true)
const history = ref(null) // 原始 { id, projectId, analysisData, generatedAt }
const report = ref(null) // 解析后的分析数据''',
'''const loading = ref(true)
const history = ref(null) // 原始 { id, projectId, analysisData, generatedAt }
const report = ref(null) // 解析后的分析数据
const status = ref(null) // 历史分析状态
const refreshing = ref(false) // 重新综合分析中''')

src = src.replace('''async function fetchHistory() {
  loading.value = true
  try {
    const res = await getHistoryAnalysis(projectId)
    if (res.code === 200) {
      history.value = res.data
      report.value = parseAnalysisData(res.data?.analysisData)
    } else {
      ElMessage.error(res.message || '加载历史分析失败')
    }
  } catch (e) {
    // 拦截器已提示
  } finally {
    loading.value = false
  }
}''',
'''async function fetchHistory() {
  loading.value = true
  try {
    const [hres, sres] = await Promise.all([
      getHistoryAnalysis(projectId),
      getHistoryStatus(projectId),
    ])
    if (hres.code === 200) {
      history.value = hres.data
      report.value = parseAnalysisData(hres.data?.analysisData)
    } else {
      ElMessage.error(hres.message || '加载历史分析失败')
    }
    if (sres.code === 200) status.value = sres.data
  } catch (e) {
    // 拦截器已提示
  } finally {
    loading.value = false
  }
}

// 重新综合分析（删除旧快照重新生成）
async function handleRegenerate() {
  refreshing.value = true
  try {
    const res = await refreshHistoryAnalysis(projectId)
    if (res.code === 200) {
      history.value = res.data
      report.value = parseAnalysisData(res.data?.analysisData)
      ElMessage.success('历史综合分析已重新生成')
      await fetchHistory()
    } else {
      ElMessage.error(res.message || '重新综合分析失败')
    }
  } catch (e) {
    // 拦截器已提示
  } finally {
    refreshing.value = false
  }
}''')

src = src.replace('''    <main class="history-body" v-loading="loading">
      <template v-if="!loading && report">''',
'''    <main class="history-body" v-loading="loading">
      <!-- 分析状态提示 -->
      <div v-if="!loading && status" class="status-alerts">
        <el-alert
          v-if="status.unanalyzedCount > 0"
          type="warning"
          :closable="false"
          show-icon
          :title="`历史里有 ${status.unanalyzedCount} 个笔试/面试已完成但尚未完成 AI 分析，分析完成后可重新综合分析`"
        />
        <el-alert
          v-if="status.hasNewAnalysis"
          type="success"
          :closable="false"
          show-icon
          title="检测到新的分析结果，可重新综合分析"
        >
          <template #default>
            <el-button
              type="primary"
              size="small"
              plain
              :loading="refreshing"
              @click="handleRegenerate"
            >
              重新综合分析
            </el-button>
          </template>
        </el-alert>
      </div>

      <template v-if="!loading && report">''')

io.open(p, 'w', encoding='utf-8', newline='').write(src)
print('history page patched')
