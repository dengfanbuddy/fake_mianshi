import io
p = 'src/views/Settings.vue'
src = io.open(p, encoding='utf-8').read()

# 1. import
old = "import { getVoiceStatus } from '../api/voice'"
if old in src:
    src = src.replace(old, "import { getVoiceStatus, getVoiceConfig, saveVoiceConfig } from '../api/voice'", 1)
    print('import patched')
else:
    print('[WARN] import anchor not found')

# 2. script 状态与方法
old2 = '''/* ---------------- 腾讯云语音配置状态 ---------------- */
const voiceConfigured = ref(null) // null=加载中, true/false
const appIdConfigured = ref(false)

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
}'''
new2 = '''/* ---------------- 腾讯云语音配置状态 ---------------- */
const voiceConfigured = ref(null) // null=加载中, true/false
const appIdConfigured = ref(false)
const voiceForm = ref({ secretId: '', secretKey: '', appId: '' })
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

// 回显当前生效的语音配置（密钥脱敏）
async function fetchVoiceConfig() {
  try {
    const res = await getVoiceConfig()
    if (res.code === 200 && res.data) {
      voiceForm.value.secretId = res.data.secretId || ''
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
      voiceForm.value.secretId = res.data?.secretId || ''
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
}'''
if old2 in src:
    src = src.replace(old2, new2, 1)
    print('script patched')
else:
    print('[WARN] script anchor not found')

# 3. onMounted
old3 = 'onMounted(() => {\n  fetchVoiceStatus()'
new3 = 'onMounted(() => {\n  fetchVoiceConfig()\n  fetchVoiceStatus()'
if old3 in src:
    src = src.replace(old3, new3, 1)
    print('onMounted patched')
else:
    print('[WARN] onMounted anchor not found')

io.open(p, 'w', encoding='utf-8', newline='').write(src)
print('done')
