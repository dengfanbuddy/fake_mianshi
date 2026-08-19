<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getAiConfigs, saveAiConfig, setActiveAiConfig } from '../api/ai-config'
import {
  getPersonaList,
  createPersona,
  updatePersona,
  deletePersona,
} from '../api/persona'
import { getVoiceStatus, getVoiceConfig, saveVoiceConfig } from '../api/voice'

/* ---------------- 腾讯云语音配置状态 ---------------- */
const voiceConfigured = ref(null) // null=加载中, true/false
const appIdConfigured = ref(false)
const voiceForm = ref({ secretId: '', secretKey: '', appId: '' })
const voiceMaskedId = ref('') // 已配置 SecretId 的脱敏回显（仅展示，不回传）
const voiceSaving = ref(false)

async function fetchVoiceStatus() {
  try {
    const res = await getVoiceStatus()
    if (res.code === 200 && res.data) {
      voiceConfigured.value = !!res.data.configured
      appIdConfigured.value = !!res.data.appIdConfigured
    }
  } catch (e) {
    // 拦截器已提示；加载失败保持 null
  }
}

// 回显当前生效的语音配置（密钥脱敏；SecretId/SecretKey 不回填输入框，避免把脱敏值误存回库）
async function fetchVoiceConfig() {
  try {
    const res = await getVoiceConfig()
    if (res.code === 200 && res.data) {
      voiceMaskedId.value = res.data.secretId || ''
      voiceForm.value.secretId = ''
      voiceForm.value.secretKey = ''
      voiceForm.value.appId = res.data.appId || ''
    }
  } catch (e) {
    // 拦截器已提示
  }
}

// 保存语音配置并激活（密钥留空保留原值）
async function saveVoiceConfigAction() {
  if (voiceSaving.value) return
  voiceSaving.value = true
  try {
    const res = await saveVoiceConfig({
      secretId: voiceForm.value.secretId.trim(),
      secretKey: voiceForm.value.secretKey.trim(),
      appId: voiceForm.value.appId.trim(),
    })
    if (res.code === 200) {
      ElMessage.success('语音配置已保存并激活，立即生效')
      voiceMaskedId.value = res.data?.secretId || ''
      voiceForm.value.secretId = ''
      voiceForm.value.secretKey = ''
      voiceForm.value.appId = res.data?.appId || ''
      await fetchVoiceStatus()
    } else {
      ElMessage.error(res.message || '保存失败')
    }
  } catch (e) {
    // 拦截器已提示
  } finally {
    voiceSaving.value = false
  }
}

/* ---------------- AI 模型配置 ---------------- */
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
    // 编辑时带 id，后端 POST /ai-config 会按 JPA save 语义更新该行
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

/* ---------------- 面试官风格管理 ---------------- */
const personas = ref([])
const personaLoading = ref(false)
const personaDialogVisible = ref(false)
const savingPersona = ref(false)
const editingPersonaId = ref(null)
const personaForm = ref({ name: '', description: '', styleConfig: '' })

async function fetchPersonas() {
  personaLoading.value = true
  try {
    const res = await getPersonaList()
    if (res.code === 200) personas.value = res.data || []
    else ElMessage.error(res.message || '加载面试官风格失败')
  } catch (e) {
    // 拦截器已提示
  } finally {
    personaLoading.value = false
  }
}

function openCreatePersona() {
  editingPersonaId.value = null
  personaForm.value = { name: '', description: '', styleConfig: '' }
  personaDialogVisible.value = true
}

function openEditPersona(row) {
  editingPersonaId.value = row.id
  personaForm.value = {
    name: row.name || '',
    description: row.description || '',
    styleConfig: row.styleConfig || '',
  }
  personaDialogVisible.value = true
}

async function handleSavePersona() {
  if (!personaForm.value.name.trim()) {
    ElMessage.warning('请填写风格名称')
    return
  }
  // 校验 styleConfig 是否为合法 JSON（非必填）
  if (personaForm.value.styleConfig.trim()) {
    try {
      JSON.parse(personaForm.value.styleConfig)
    } catch (e) {
      ElMessage.warning('styleConfig 必须是合法 JSON')
      return
    }
  }
  savingPersona.value = true
  try {
    const payload = {
      name: personaForm.value.name.trim(),
      description: personaForm.value.description.trim(),
      styleConfig: personaForm.value.styleConfig.trim(),
    }
    const res = editingPersonaId.value
      ? await updatePersona(editingPersonaId.value, payload)
      : await createPersona(payload)
    if (res.code === 200) {
      ElMessage.success(editingPersonaId.value ? '风格已更新' : '风格已创建')
      personaDialogVisible.value = false
      await fetchPersonas()
    } else {
      ElMessage.error(res.message || '保存失败')
    }
  } catch (e) {
    // 拦截器已提示
  } finally {
    savingPersona.value = false
  }
}

async function handleDeletePersona(row) {
  try {
    await ElMessageBox.confirm(`确定删除自定义风格「${row.name}」吗？`, '删除确认', {
      type: 'warning',
    })
  } catch (e) {
    return
  }
  try {
    const res = await deletePersona(row.id)
    if (res.code === 200) {
      ElMessage.success('删除成功')
      await fetchPersonas()
    } else {
      ElMessage.error(res.message || '删除失败')
    }
  } catch (e) {
    // 拦截器已提示
  }
}

onMounted(() => {
  fetchConfigs()
  fetchPersonas()
  fetchVoiceConfig()
  fetchVoiceStatus()
})
</script>

<template>
  <div class="settings">
    <!-- AI 模型配置 -->
    <el-card class="section" shadow="never">
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
            <el-button
              type="primary"
              size="small"
              text
              @click="openEditConfig(row)"
            >编辑</el-button>
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
    </el-card>

    <!-- 腾讯云语音配置（页面可配置、存库激活） -->
    <el-card class="section" shadow="never">
      <template #header>
        <span>腾讯云语音配置（TTS/ASR）</span>
      </template>

      <el-form label-width="110px" class="voice-form">
        <el-form-item label="SecretId">
          <el-input
            v-model="voiceForm.secretId"
            placeholder="留空保留原值，填写新值则覆盖"
            show-password
          />
          <div v-if="voiceMaskedId" class="hint">已配置：{{ voiceMaskedId }}</div>
        </el-form-item>
        <el-form-item label="SecretKey">
          <el-input
            v-model="voiceForm.secretKey"
            placeholder="留空保留原值，填写新值则覆盖"
            show-password
          />
        </el-form-item>
        <el-form-item label="AppId">
          <el-input v-model="voiceForm.appId" placeholder="腾讯云 AppId（实时语音识别需要）" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="voiceSaving" @click="saveVoiceConfigAction">
            保存并激活
          </el-button>
          <span class="hint" style="margin-left: 12px">保存后立即生效，无需重启服务</span>
        </el-form-item>
      </el-form>

      <el-alert type="info" :closable="false">
        <div>
          状态：
          <el-tag v-if="voiceConfigured === null" type="info" size="small" effect="plain">检测中…</el-tag>
          <el-tag v-else-if="voiceConfigured" type="success" size="small" effect="dark">已配置</el-tag>
          <el-tag v-else type="warning" size="small" effect="plain">未配置</el-tag>
          <span class="hint" v-if="voiceConfigured === false">
            请在下方填写腾讯云密钥并保存，或通过后端环境变量 TENCENT_SECRET_ID / TENCENT_SECRET_KEY 配置。
          </span>
          <span class="hint" v-else-if="voiceConfigured">STT / TTS 已可用。</span>
        </div>
        <div class="hint" v-if="voiceConfigured && !appIdConfigured">
          提示：未配置 AppId，部分语音能力（如录音文件识别）可能受限，一句话识别不受影响。
        </div>
        <div class="hint">
          配置保存在本地数据库中（密钥仅脱敏回显，不会明文下发到浏览器），打包分发后直接在页面填写即可，无需修改环境变量。
        </div>
      </el-alert>
    </el-card>

    <!-- 面试官风格管理 -->
    <el-card class="section" shadow="never">
      <template #header>
        <div class="section-header">
          <span>面试官风格</span>
          <el-button type="primary" size="small" @click="openCreatePersona">新增自定义风格</el-button>
        </div>
      </template>

      <el-table v-loading="personaLoading" :data="personas" border stripe>
        <el-table-column prop="name" label="名称" min-width="140" />
        <el-table-column prop="description" label="描述" min-width="220" show-overflow-tooltip />
        <el-table-column label="类型" width="110">
          <template #default="{ row }">
            <el-tag v-if="row.isPreset" type="info" size="small" effect="plain">系统预置</el-tag>
            <el-tag v-else type="warning" size="small" effect="plain">自定义</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150">
          <template #default="{ row }">
            <el-button
              v-if="!row.isPreset"
              type="primary"
              size="small"
              text
              @click="openEditPersona(row)"
            >编辑</el-button>
            <el-button
              v-if="!row.isPreset"
              type="danger"
              size="small"
              text
              @click="handleDeletePersona(row)"
            >删除</el-button>
            <el-tooltip v-else content="预置风格不可修改" placement="top">
              <span class="disabled-text">—</span>
            </el-tooltip>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- AI 配置新增/编辑弹窗 -->
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
          <el-input v-model="configForm.apiUrl" placeholder="OpenAI 兼容端点，例如：https://api.deepseek.com/v1/chat/completions" />
        </el-form-item>
        <el-form-item label="API Key">
          <el-input v-model="configForm.apiKey" type="password" show-password placeholder="可留空（本地配置时填）" />
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

    <!-- 面试官风格新增/编辑弹窗 -->
    <el-dialog
      v-model="personaDialogVisible"
      :title="editingPersonaId ? '编辑自定义风格' : '新增自定义风格'"
      width="520px"
    >
      <el-form label-width="90px">
        <el-form-item label="名称" required>
          <el-input v-model="personaForm.name" placeholder="例如：严格技术官" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="personaForm.description" type="textarea" :rows="3" placeholder="风格简要描述" />
        </el-form-item>
        <el-form-item label="风格配置">
          <el-input
            v-model="personaForm.styleConfig"
            type="textarea"
            :rows="5"
            placeholder='JSON，例如：{"tone":"strict","interrupt":true}'
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="personaDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="savingPersona" @click="handleSavePersona">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.settings {
  max-width: 1100px;
  margin: 0 auto;
}
.section {
  margin-bottom: 20px;
}
.section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.voice-alert {
  line-height: 1.9;
}
.code-line {
  font-family: 'Courier New', monospace;
  background: #f0f2f5;
  padding: 2px 6px;
  border-radius: 4px;
  margin: 6px 0;
  display: inline-block;
}
.hint {
  margin-left: 8px;
  color: #909399;
  font-size: 13px;
}
.active-hint {
  color: #909399;
  font-size: 13px;
}
.disabled-text {
  color: #c0c4cc;
}
</style>
