<script setup>
/**
 * AI 模型配置面板（从 Settings.vue 拆出）。
 * 只负责「供应商 / 端点 / 模型名 / 激活」的增删改查，不涉及语音与人设。
 */
import { ref, onMounted } from 'vue'
import { getAiConfigs, saveAiConfig, setActiveAiConfig } from '../../api/ai-config'

const configs = ref([])
const configLoading = ref(false)
const configDialogVisible = ref(false)
const savingConfig = ref(false)
const editingConfigId = ref(null)
const configForm = ref({ provider: '', apiUrl: '', apiKey: '', modelName: '', isActive: true })

async function fetchConfigs() {
  configLoading.value = true
  try {
    const res = await getAiConfigs()
    if (res.code === 200) configs.value = res.data || []
    else ElMessage.error(res.message || '加载 AI 配置失败')
  } catch (e) {
    // 拦截器已提示
  } finally {
    configLoading.value = false
  }
}

function openConfigDialog() {
  editingConfigId.value = null
  configForm.value = { provider: '', apiUrl: '', apiKey: '', modelName: '', isActive: true }
  configDialogVisible.value = true
}

function openEditConfig(row) {
  editingConfigId.value = row.id
  configForm.value = {
    provider: row.provider || '',
    apiUrl: row.apiUrl || '',
    apiKey: row.apiKey || '',
    modelName: row.modelName || '',
    isActive: !!row.isActive,
  }
  configDialogVisible.value = true
}

async function handleSaveConfig() {
  if (!configForm.value.provider.trim() || !configForm.value.apiUrl.trim()) {
    ElMessage.warning('请填写 provider 和 apiUrl')
    return
  }
  savingConfig.value = true
  try {
    const payload = {
      provider: configForm.value.provider.trim(),
      apiUrl: configForm.value.apiUrl.trim(),
      apiKey: configForm.value.apiKey.trim(),
      modelName: configForm.value.modelName.trim(),
      isActive: configForm.value.isActive,
    }
    // 编辑时带 id，后端 POST /ai-config 会按 save 语义更新该行
    if (editingConfigId.value) payload.id = editingConfigId.value
    const res = await saveAiConfig(payload)
    if (res.code === 200) {
      ElMessage.success(editingConfigId.value ? 'AI 配置已更新' : 'AI 配置保存成功')
      configDialogVisible.value = false
      await fetchConfigs()
    } else {
      ElMessage.error(res.message || '保存失败')
    }
  } catch (e) {
    // 拦截器已提示
  } finally {
    savingConfig.value = false
  }
}

async function handleSetActive(row) {
  try {
    const res = await setActiveAiConfig(row.id)
    if (res.code === 200) {
      ElMessage.success(`已启用 ${row.provider}`)
      await fetchConfigs()
    } else {
      ElMessage.error(res.message || '启用失败')
    }
  } catch (e) {
    // 拦截器已提示
  }
}

onMounted(fetchConfigs)
</script>

<template>
  <el-card class="panel" shadow="never">
    <template #header>
      <div class="section-header">
        <span>AI 模型配置</span>
        <el-button type="primary" size="small" @click="openConfigDialog">新增配置</el-button>
      </div>
    </template>

    <el-table v-loading="configLoading" :data="configs" border stripe>
      <el-table-column prop="provider" label="Provider" min-width="120" />
      <el-table-column prop="apiUrl" label="API 地址" min-width="200" show-overflow-tooltip />
      <el-table-column prop="modelName" label="模型名" min-width="140" show-overflow-tooltip />
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag v-if="row.isActive" type="success" size="small" effect="dark">已启用</el-tag>
          <el-tag v-else type="info" size="small" effect="plain">未启用</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="180">
        <template #default="{ row }">
          <el-button type="primary" size="small" text @click="openEditConfig(row)">编辑</el-button>
          <el-button
            v-if="!row.isActive"
            type="warning"
            size="small"
            text
            @click="handleSetActive(row)"
          >设为激活</el-button>
          <span v-else class="active-hint">当前启用</span>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog
      v-model="configDialogVisible"
      :title="editingConfigId ? '编辑 AI 模型配置' : '新增 AI 模型配置'"
      width="520px"
    >
      <el-form label-width="90px">
        <el-form-item label="Provider" required>
          <el-input v-model="configForm.provider" placeholder="例如：deepseek / openai / qwen" />
        </el-form-item>
        <el-form-item label="API 地址" required>
          <el-input
            v-model="configForm.apiUrl"
            placeholder="OpenAI 兼容端点，例如：https://api.deepseek.com/v1/chat/completions"
          />
        </el-form-item>
        <el-form-item label="API Key">
          <el-input
            v-model="configForm.apiKey"
            type="password"
            show-password
            placeholder="可留空（本地配置时填）"
          />
        </el-form-item>
        <el-form-item label="模型名">
          <el-input v-model="configForm.modelName" placeholder="例如：deepseek-chat" />
        </el-form-item>
        <el-form-item label="设为启用">
          <el-switch v-model="configForm.isActive" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="configDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="savingConfig" @click="handleSaveConfig">保存</el-button>
      </template>
    </el-dialog>
  </el-card>
</template>

<style scoped>
.panel {
  margin-bottom: 20px;
}
.section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.active-hint {
  color: #909399;
  font-size: 13px;
}
</style>
