<script setup>
/**
 * 面试官风格面板（从 Settings.vue 拆出，并把裸 JSON 输入框换成表单）。
 *
 * 拆出来的第二个理由：`styleConfig` 是个二维概念——一半进 LLM 提示词（问什么），
 * 一半进 TTS（怎么念），而且 0.6B 权重会丢弃 TTS 的风格指令。
 * 用表单分组 + 明确标注，比让用户看一行 JSON 靠谱。
 */
import { computed, ref, onMounted, onBeforeUnmount } from 'vue'
import { getPersonaList, createPersona, updatePersona, deletePersona } from '../../api/persona'
import { synthesizeSpeech, getVoiceStatus } from '../../api/voice'
import {
  VOICE_OPTIONS,
  TONE_OPTIONS,
  FOLLOWUP_OPTIONS,
  SPEED_OPTIONS,
  LANGUAGE_OPTIONS,
  aggressivenessLabel,
  emptyPersonaStyleForm,
  parseStyleConfig,
  buildStyleConfig,
  summarizeStyleConfig,
  ineffectiveKeys,
} from './personaStyle'

const personas = ref([])
const personaLoading = ref(false)
const personaDialogVisible = ref(false)
const savingPersona = ref(false)
const editingPersonaId = ref(null)
const personaForm = ref({ name: '', description: '' })
/** styleConfig 的唯一真相：表单改动都写回这里，保证两者不会不一致 */
const styleJson = ref('')
const advanced = ref(false)
const previewing = ref(false)
const providerIsLocal = ref(false)

let previewAudio = null
let previewUrl = null

const styleForm = computed(() => parseStyleConfig(styleJson.value).form)
const styleInvalid = computed(() => !parseStyleConfig(styleJson.value).valid)
const styleSummary = computed(() => summarizeStyleConfig(styleJson.value))
const ignoredHere = computed(() => ineffectiveKeys(styleForm.value))

/** 表单字段改动 → 写回 JSON（JSON 始终是唯一真相） */
function setField(key, value) {
  const next = { ...styleForm.value, [key]: value }
  styleJson.value = buildStyleConfig(next)
}

const aggressivenessValue = computed(() =>
  styleForm.value.aggressiveness === '' ? 0.5 : Number(styleForm.value.aggressiveness)
)

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

async function fetchProvider() {
  try {
    const res = await getVoiceStatus()
    if (res.code === 200 && res.data) providerIsLocal.value = !!res.data.local
  } catch (e) {
    // 读不到就按腾讯云处理，仅影响提示文案
  }
}

function openCreatePersona() {
  editingPersonaId.value = null
  personaForm.value = { name: '', description: '' }
  styleJson.value = ''
  advanced.value = false
  personaDialogVisible.value = true
}

function openEditPersona(row) {
  editingPersonaId.value = row.id
  personaForm.value = { name: row.name || '', description: row.description || '' }
  styleJson.value = row.styleConfig || ''
  advanced.value = false
  personaDialogVisible.value = true
}

async function handleSavePersona() {
  if (!personaForm.value.name.trim()) {
    ElMessage.warning('请填写风格名称')
    return
  }
  if (styleJson.value.trim()) {
    try {
      JSON.parse(styleJson.value)
    } catch (e) {
      ElMessage.warning('风格配置不是合法 JSON，请检查「高级」里的内容')
      return
    }
  }
  savingPersona.value = true
  try {
    const payload = {
      name: personaForm.value.name.trim(),
      description: personaForm.value.description.trim(),
      styleConfig: styleJson.value.trim(),
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

/** 试听：用当前表单生成的 styleConfig 真合成一句，直接听效果 */
async function previewVoice() {
  if (previewing.value) return
  previewing.value = true
  try {
    const blob = await synthesizeSpeech(
      '你好，我是本次面试官，我们直接开始吧，先讲讲你最近做的一个项目。',
      styleJson.value
    )
    if (!blob || blob.size === 0) {
      ElMessage.error('试听失败：音频为空')
      return
    }
    stopPreview()
    previewUrl = URL.createObjectURL(blob)
    previewAudio = new Audio(previewUrl)
    previewAudio.onended = () => {
      URL.revokeObjectURL(previewUrl)
      previewUrl = null
      previewAudio = null
    }
    await previewAudio.play()
  } catch (e) {
    ElMessage.error(`试听失败：${e?.response?.data?.message || e?.message || '未知错误'}`)
  } finally {
    previewing.value = false
  }
}

function stopPreview() {
  if (previewAudio) {
    try { previewAudio.pause() } catch { /* ignore */ }
    previewAudio = null
  }
  if (previewUrl) {
    URL.revokeObjectURL(previewUrl)
    previewUrl = null
  }
}

onMounted(() => {
  fetchPersonas()
  fetchProvider()
})

onBeforeUnmount(stopPreview)
</script>

<template>
  <el-card class="panel" shadow="never">
    <template #header>
      <div class="section-header">
        <span>面试官风格</span>
        <el-button type="primary" size="small" @click="openCreatePersona">新增自定义风格</el-button>
      </div>
    </template>

    <el-table v-loading="personaLoading" :data="personas" border stripe>
      <el-table-column prop="name" label="名称" min-width="130" />
      <el-table-column prop="description" label="描述" min-width="180" show-overflow-tooltip />
      <el-table-column label="风格摘要" min-width="260" show-overflow-tooltip>
        <template #default="{ row }">
          <span class="style-summary">{{ summarizeStyleConfig(row.styleConfig) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="类型" width="100">
        <template #default="{ row }">
          <el-tag v-if="row.isPreset" type="info" size="small" effect="plain">系统预置</el-tag>
          <el-tag v-else type="warning" size="small" effect="plain">自定义</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="150">
        <template #default="{ row }">
          <el-button v-if="!row.isPreset" type="primary" size="small" text @click="openEditPersona(row)">
            编辑
          </el-button>
          <el-button v-if="!row.isPreset" type="danger" size="small" text @click="handleDeletePersona(row)">
            删除
          </el-button>
          <el-tooltip v-else content="预置风格不可修改" placement="top">
            <span class="disabled-text">—</span>
          </el-tooltip>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog
      v-model="personaDialogVisible"
      :title="editingPersonaId ? '编辑自定义风格' : '新增自定义风格'"
      width="640px"
      @closed="stopPreview"
    >
      <el-form label-width="110px">
        <el-form-item label="名称" required>
          <el-input v-model="personaForm.name" placeholder="例如：严格技术官" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input
            v-model="personaForm.description"
            type="textarea"
            :rows="2"
            placeholder="风格简述，会拼进面试官提示词"
          />
          <div class="hint">名称 + 描述直接进 LLM 提示词，是「问什么」的主要依据。</div>
        </el-form-item>

        <el-divider content-position="left">提问方式（进提示词，立即生效）</el-divider>

        <el-form-item label="追问策略">
          <el-select
            :model-value="styleForm.followupStrategy"
            placeholder="选择追问方向"
            clearable
            style="width: 100%"
            @update:model-value="(v) => setField('followupStrategy', v || '')"
          >
            <el-option
              v-for="o in FOLLOWUP_OPTIONS"
              :key="o.value"
              :label="o.label"
              :value="o.value"
            >
              <span>{{ o.label }}</span>
              <span class="option-detail">{{ o.detail }}</span>
            </el-option>
          </el-select>
          <div class="hint">
            {{ FOLLOWUP_OPTIONS.find((o) => o.value === styleForm.followupStrategy)?.detail || '留空则根据回答动态深挖' }}
          </div>
        </el-form-item>

        <el-form-item label="语气">
          <el-select
            :model-value="styleForm.tone"
            placeholder="选择语气"
            clearable
            style="width: 100%"
            @update:model-value="(v) => setField('tone', v || '')"
          >
            <el-option v-for="o in TONE_OPTIONS" :key="o.value" :label="o.label" :value="o.value">
              <span>{{ o.label }}</span>
              <span class="option-detail">{{ o.detail }}</span>
            </el-option>
          </el-select>
          <div class="hint">
            {{ TONE_OPTIONS.find((o) => o.value === styleForm.tone)?.detail || '不影响措辞' }}。
            语气同时决定默认音色（如严肃 → uncle_fu）。
          </div>
        </el-form-item>

        <el-form-item label="追问强度">
          <el-slider
            :model-value="aggressivenessValue"
            :min="0"
            :max="1"
            :step="0.1"
            show-stops
            :format-tooltip="(v) => `${v} · ${aggressivenessLabel(v)}`"
            style="width: 70%"
            @update:model-value="(v) => setField('aggressiveness', v)"
          />
          <el-tag size="small" style="margin-left: 12px">{{ aggressivenessLabel(aggressivenessValue) }}</el-tag>
          <div class="hint">
            真正影响提问烈度：≥0.8 会质疑结论、要求给数据与归因、连续追问三层以上；
            ≤0.3 会主动给提示、允许换题。这一项<strong>只影响文字内容，不影响声音</strong>。
          </div>
        </el-form-item>

        <el-divider content-position="left">声音（本地 Qwen TTS）</el-divider>

        <el-alert
          v-if="!providerIsLocal"
          type="warning"
          :closable="false"
          style="margin-bottom: 12px"
        >
          当前语音供应商不是本地 Qwen，音色由腾讯云决定，下面的音色/语速不会生效。
        </el-alert>

        <el-form-item label="音色">
          <el-select
            :model-value="styleForm.qwenVoice"
            placeholder="留空则跟随语气"
            clearable
            style="width: 100%"
            @update:model-value="(v) => setField('qwenVoice', v || '')"
          >
            <el-option v-for="o in VOICE_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
          <div class="hint">
            可选音色由模型固定，共 {{ VOICE_OPTIONS.length }} 个。留空时随语气自动选
            （{{ TONE_OPTIONS.find((o) => o.value === styleForm.tone)?.speaker || 'uncle_fu' }}）。
          </div>
        </el-form-item>

        <el-form-item label="语种">
          <el-select
            :model-value="styleForm.qwenLanguage"
            style="width: 100%"
            @update:model-value="(v) => setField('qwenLanguage', v)"
          >
            <el-option v-for="o in LANGUAGE_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
        </el-form-item>

        <el-alert type="info" :closable="false" style="margin-bottom: 12px">
          下列两项只有换成 <strong>1.7B</strong> 权重的 Qwen3-TTS 才会生效：
          当前 0.6B 模型在推理层直接把风格指令置空（<code>instruct = None</code>），
          填了也不会改变声音。
        </el-alert>

        <el-form-item label="语速（需 1.7B）">
          <el-select
            :model-value="styleForm.speed"
            placeholder="留空 = 模型默认语速"
            clearable
            style="width: 100%"
            @update:model-value="(v) => setField('speed', v || '')"
          >
            <el-option v-for="o in SPEED_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
        </el-form-item>

        <el-form-item label="语气指令（需 1.7B）">
          <el-input
            :model-value="styleForm.qwenInstruct"
            type="textarea"
            :rows="2"
            placeholder="自然语言描述，例如：语速偏慢、压低声音、带一点审视感"
            @update:model-value="(v) => setField('qwenInstruct', v)"
          />
          <div class="hint">填了会覆盖上面的语气与语速描述。</div>
        </el-form-item>

        <el-form-item label="试听">
          <el-button :loading="previewing" @click="previewVoice">用当前配置念一句</el-button>
          <div class="hint">走的是真实 TTS 链路，能立刻听出音色对不对。</div>
        </el-form-item>

        <el-form-item label="效果预览">
          <span class="style-summary">{{ styleSummary }}</span>
          <div v-if="ignoredHere.length" class="hint error-hint">
            当前权重下不生效：{{ ignoredHere.join('、') }}
          </div>
        </el-form-item>

        <el-form-item label="高级">
          <el-switch v-model="advanced" active-text="直接编辑 JSON" />
          <el-input
            v-if="advanced"
            v-model="styleJson"
            type="textarea"
            :rows="4"
            style="margin-top: 8px"
            placeholder='{"tone":"serious","aggressiveness":0.8,"followupStrategy":"deep_dive"}'
          />
          <div v-if="advanced && styleInvalid" class="hint error-hint">不是合法 JSON，保存会被拦下</div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="personaDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="savingPersona" @click="handleSavePersona">保存</el-button>
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
.hint {
  margin-left: 8px;
  margin-top: 4px;
  color: #909399;
  font-size: 13px;
}
.error-hint {
  color: #f56c6c;
}
.option-detail {
  margin-left: 10px;
  color: #a8abb2;
  font-size: 12px;
}
.style-summary {
  color: #606266;
  font-size: 13px;
}
.disabled-text {
  color: #c0c4cc;
}
</style>
