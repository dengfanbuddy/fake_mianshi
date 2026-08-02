<script setup>
import { ref, onMounted, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowLeft, Refresh, MagicStick } from '@element-plus/icons-vue'
import {
  getPromptTemplates,
  getPromptOccupations,
  savePromptTemplate,
  regeneratePromptTemplate,
  ensurePromptTemplates,
} from '../api/prompt-template'
import { useRouter } from 'vue-router'

const router = useRouter()

const occupations = ref([])
const currentOccupation = ref('default')
const templates = ref([]) // 当前职业的场景模板
const loading = ref(false)
const savingScene = ref('')
const regeneratingScene = ref('')

// 场景用途说明
const SCENE_INFO = {
  WRITTEN_QUESTION: '笔试出题：AI 生成笔试题时使用的系统指令',
  MOCK_OUTLINE: '模拟面试大纲：AI 制定面试考察重点时使用的系统指令',
  WRITTEN_ANALYSIS: '笔试分析报告：AI 分析笔试结果、生成查漏补缺报告时使用的系统指令',
  MOCK_ANALYSIS: '模拟面试分析报告：AI 分析面试对话、生成评估报告时使用的系统指令',
  HISTORY_ANALYSIS: '历史综合分析：AI 跨多次面试做趋势与成长规划时使用的系统指令',
}

async function fetchOccupations() {
  try {
    const res = await getPromptOccupations()
    if (res.code === 200) occupations.value = res.data || []
  } catch (e) {
    // 拦截器已提示
  }
}

async function fetchTemplates() {
  loading.value = true
  try {
    const res = await getPromptTemplates(currentOccupation.value)
    if (res.code === 200) {
      templates.value = res.data || []
    }
  } catch (e) {
    // 拦截器已提示
  } finally {
    loading.value = false
  }
}

watch(currentOccupation, fetchTemplates)

async function handleSave(tpl) {
  savingScene.value = tpl.scene
  try {
    const res = await savePromptTemplate({
      occupation: currentOccupation.value,
      scene: tpl.scene,
      content: tpl.content,
    })
    if (res.code === 200) {
      ElMessage.success('提示词已保存')
    } else {
      ElMessage.error(res.message || '保存失败')
    }
  } catch (e) {
    // 拦截器已提示
  } finally {
    savingScene.value = ''
  }
}

async function handleRegenerate(tpl) {
  try {
    await ElMessageBox.confirm(
      `AI 将针对「${currentOccupation.value}」职业重新生成「${tpl.sceneLabel}」提示词（会覆盖当前内容），是否继续？`,
      'AI 优化提示词',
      { type: 'warning', confirmButtonText: '生成', cancelButtonText: '取消' }
    )
  } catch (e) {
    return // 取消
  }
  regeneratingScene.value = tpl.scene
  try {
    const res = await regeneratePromptTemplate({
      occupation: currentOccupation.value,
      scene: tpl.scene,
    })
    if (res.code === 200) {
      if (res.data) {
        const idx = templates.value.findIndex((t) => t.scene === res.data.scene)
        if (idx >= 0) templates.value[idx].content = res.data.content
      }
      ElMessage.success('AI 已重新生成并保存')
    } else {
      ElMessage.error(res.message || '生成失败')
    }
  } catch (e) {
    // 拦截器已提示
  } finally {
    regeneratingScene.value = ''
  }
}

// 补齐当前职业缺失的模板（新职业）
async function handleEnsure() {
  if (currentOccupation.value === 'default') return
  loading.value = true
  try {
    const res = await ensurePromptTemplates(currentOccupation.value)
    if (res.code === 200) {
      const scenes = res.data?.generatedScenes || []
      if (scenes.length) {
        ElMessage.success(`已为新职业生成 ${scenes.length} 个场景提示词`)
      } else {
        ElMessage.info('该职业提示词已齐全')
      }
      await fetchTemplates()
    }
  } catch (e) {
    // 拦截器已提示
  } finally {
    loading.value = false
  }
}

function goBack() {
  router.push('/settings')
}

onMounted(async () => {
  await fetchOccupations()
  await fetchTemplates()
})
</script>

<template>
  <div class="prompt-page">
    <header class="top-bar">
      <el-button class="back-btn" text :icon="ArrowLeft" @click="goBack">返回</el-button>
      <h1 class="top-title">提示词管理</h1>
      <div class="top-tag">
        <el-tag type="primary" effect="dark">按职业分类</el-tag>
      </div>
    </header>

    <main class="prompt-body">
      <el-alert type="info" :closable="false" class="prompt-alert">
        <div>
          提示词是系统调用 AI 时使用的指令。不同职业可使用专属提示词（题目更贴合岗位）；
          未配置的职业自动使用「default」通用模板。模板中的
          <code>{position}</code> 会在使用时替换为项目的目标岗位。
        </div>
      </el-alert>

      <div class="prompt-toolbar">
        <el-select v-model="currentOccupation" placeholder="选择职业" style="width: 260px" filterable>
          <el-option
            v-for="occ in occupations"
            :key="occ"
            :label="occ === 'default' ? 'default（通用模板）' : occ"
            :value="occ"
          />
        </el-select>
        <el-button type="primary" plain :loading="loading" @click="handleEnsure">
          补齐缺失模板
        </el-button>
      </div>

      <div v-loading="loading" class="scene-list">
        <div v-for="tpl in templates" :key="tpl.scene" class="scene-card">
          <div class="scene-header">
            <span class="scene-name">{{ tpl.sceneLabel }}</span>
            <span class="scene-desc">{{ SCENE_INFO[tpl.scene] || '' }}</span>
            <div class="scene-actions">
              <el-button
                type="primary"
                size="small"
                :loading="savingScene === tpl.scene"
                @click="handleSave(tpl)"
              >
                保存
              </el-button>
              <el-button
                type="warning"
                size="small"
                plain
                :icon="MagicStick"
                :loading="regeneratingScene === tpl.scene"
                @click="handleRegenerate(tpl)"
              >
                AI 优化
              </el-button>
            </div>
          </div>
          <el-input
            v-model="tpl.content"
            type="textarea"
            :rows="10"
            class="scene-content"
          />
          <div class="scene-foot">更新于 {{ tpl.updatedAt ? String(tpl.updatedAt).replace('T', ' ').slice(0, 19) : '—' }}</div>
        </div>
      </div>
    </main>
  </div>
</template>

<style scoped>
.prompt-page {
  min-height: 100%;
  display: flex;
  flex-direction: column;
}
.top-bar {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px 20px;
  background: #fff;
  border-bottom: 1px solid #ebeef5;
}
.top-title {
  font-size: 17px;
  font-weight: 600;
  margin: 0;
  flex: 1;
}
.prompt-body {
  max-width: 920px;
  margin: 0 auto;
  padding: 18px;
  width: 100%;
  box-sizing: border-box;
  display: flex;
  flex-direction: column;
  gap: 14px;
}
.prompt-alert {
  font-size: 13px;
}
.prompt-alert code {
  background: #f0f2f5;
  padding: 1px 6px;
  border-radius: 3px;
}
.prompt-toolbar {
  display: flex;
  gap: 12px;
  align-items: center;
}
.scene-list {
  display: flex;
  flex-direction: column;
  gap: 14px;
  min-height: 200px;
}
.scene-card {
  background: #fff;
  border: 1px solid #ebeef5;
  border-radius: 10px;
  padding: 14px 16px;
}
.scene-header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 10px;
}
.scene-name {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
}
.scene-desc {
  font-size: 12px;
  color: #909399;
  flex: 1;
}
.scene-actions {
  display: flex;
  gap: 8px;
}
.scene-content {
  font-family: 'Consolas', 'Courier New', monospace;
  font-size: 12px;
  line-height: 1.6;
}
.scene-foot {
  margin-top: 6px;
  font-size: 12px;
  color: #c0c4cc;
}
</style>
