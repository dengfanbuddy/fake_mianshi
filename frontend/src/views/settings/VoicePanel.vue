<script setup>
/**
 * 语音服务配置面板（从 Settings.vue 拆出）。
 *
 * 管理「供应商（腾讯云 / 本地 Qwen）+ sidecar 进程 + 腾讯云密钥 + 识别热词」，
 * 以及本地 TTS/ASR 的健康状态展示。麦克风自检拆在 MicSelfTest.vue。
 */
import { computed, ref, onMounted, onBeforeUnmount } from 'vue'
import MicSelfTest from './MicSelfTest.vue'
import {
  getVoiceStatus,
  getVoiceConfig,
  saveVoiceConfig,
  getLocalVoiceHealth,
  getLocalVoiceProcess,
  startLocalVoice,
  stopLocalVoice,
} from '../../api/voice'

const voiceProvider = ref('tencent') // tencent | local_qwen
const localVoiceUrl = ref('http://127.0.0.1:9100')
const localHealth = ref(null)
const localTesting = ref(false)

/** 后端托管的 sidecar 进程状态（PID / 是否托管 / 最近日志） */
const localProcess = ref(null)
const localStarting = ref(false)
const localStopping = ref(false)
/** 启动后的健康轮询句柄：必须在 onBeforeUnmount 清理，否则离开页面还在跑 */
let localPollTimer = null

async function fetchLocalHealth() {
  const res = await getLocalVoiceHealth()
  if (res.code === 200) {
    localHealth.value = res.data || {}
  }
  return localHealth.value
}

async function fetchLocalProcess() {
  try {
    const res = await getLocalVoiceProcess()
    if (res.code === 200) {
      localProcess.value = res.data || null
    }
  } catch (e) {
    // 状态接口失败不打扰用户，健康检查那块会给出可见反馈
  }
}

async function testLocalVoice() {
  if (localTesting.value) return
  localTesting.value = true
  try {
    await fetchLocalHealth()
    const health = localHealth.value || {}
    if (health.reachable) {
      ElMessage.success(
        health.loaded
          ? `本地语音服务正常（${health.kind || 'qwen'}，${health.speakers?.length || 0} 个音色）`
          : '本地语音服务已连通，模型正在加载…'
      )
    } else {
      ElMessage.warning('无法连通本地语音服务，可点「启动本地服务」由后端拉起')
    }
  } catch (e) {
    localHealth.value = { reachable: false }
  } finally {
    localTesting.value = false
  }
}

function stopLocalPolling() {
  if (localPollTimer) {
    clearInterval(localPollTimer)
    localPollTimer = null
  }
}

/** 启动后每 2 秒刷一次状态与健康，直到模型就绪（约 20s）或 60s 超时 */
function startLocalPolling() {
  stopLocalPolling()
  let ticks = 0
  localPollTimer = setInterval(async () => {
    ticks += 1
    await Promise.all([fetchLocalProcess(), fetchLocalHealth().catch(() => null)])
    const health = localHealth.value
    if ((health && health.reachable && health.loaded) || ticks >= 30) {
      stopLocalPolling()
    }
  }, 2000)
}

async function startLocalVoiceService() {
  if (localStarting.value) return
  localStarting.value = true
  try {
    const res = await startLocalVoice()
    if (res.code === 200) {
      const data = res.data || {}
      if (data.started) {
        ElMessage.success(data.message || '已启动本地语音服务')
      } else {
        ElMessage.info(data.message || '本地语音服务已在运行')
      }
      await fetchLocalProcess()
      startLocalPolling()
    }
  } catch (e) {
    // 拦截器已提示
  } finally {
    localStarting.value = false
  }
}

async function stopLocalVoiceService() {
  if (localStopping.value) return
  // 不是后端托管、但端口上有服务在跑：这多半是崩溃遗留的孤儿，允许强制结束（后端会校验 cwd）
  const force = !localProcess.value?.managed
  localStopping.value = true
  try {
    const res = await stopLocalVoice(force)
    if (res.code === 200) {
      const data = res.data || {}
      if (data.stopped && data.freed === false) {
        // 端口还开着 = 显存大概率没释放，必须明确告警而不是报成功
        ElMessage.warning(data.message || '进程已结束，但端口仍在监听，显存可能未释放')
      } else if (data.stopped) {
        ElMessage.success(data.message || '已结束本地语音服务')
        localHealth.value = { reachable: false }
      } else {
        ElMessage.info(data.message || '当前没有由后端托管的进程')
      }
      await fetchLocalProcess()
      stopLocalPolling()
    }
  } catch (e) {
    // 拦截器已提示
  } finally {
    localStopping.value = false
  }
}

const localProcessText = computed(() => {
  const proc = localProcess.value
  const reachable = Boolean(localHealth.value?.reachable)
  if (proc?.managed) {
    return reachable ? `后端托管中 · PID ${proc.pid}` : `后端已拉起 · PID ${proc.pid} · 加载中`
  }
  if (reachable) {
    return '外部实例运行中（后端关闭时不会结束它）'
  }
  return '未运行'
})

const localProcessTagType = computed(() => {
  if (localProcess.value?.managed) return 'success'
  if (localHealth.value?.reachable) return 'info'
  return 'warning'
})

/* ---------------- 腾讯云语音配置 ---------------- */
const voiceConfigured = ref(null) // null=加载中, true/false
const appIdConfigured = ref(false)
const voiceForm = ref({ secretId: '', secretKey: '', appId: '', asrPrompt: '' })
const asrPromptDefault = ref('') // 内置默认热词（后端下发，只读）
const voiceMaskedId = ref('') // 已配置 SecretId 的脱敏回显（仅展示，不回传）
const voiceSaving = ref(false)

async function fetchVoiceStatus() {
  try {
    const res = await getVoiceStatus()
    if (res.code === 200 && res.data) {
      voiceConfigured.value = !!res.data.configured
      appIdConfigured.value = !!res.data.appIdConfigured
      if (res.data.provider) {
        voiceProvider.value = res.data.local ? 'local_qwen' : 'tencent'
      }
      if (res.data.localUrl) {
        localVoiceUrl.value = res.data.localUrl
      }
    }
  } catch (e) {
    // 拦截器已提示；加载失败按未配置处理，避免永久显示"检测中"
    voiceConfigured.value = false
    appIdConfigured.value = false
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
      voiceForm.value.asrPrompt = res.data.asrPrompt || ''
      asrPromptDefault.value = res.data.asrPromptDefault || ''
    }
  } catch (e) {
    // 拦截器已提示
  }
}

// 把内置默认热词填进输入框（用户在此基础上改，比从零写好用）
function fillDefaultAsrPrompt() {
  if (!asrPromptDefault.value) {
    ElMessage.info('后端未配置内置热词')
    return
  }
  voiceForm.value.asrPrompt = asrPromptDefault.value
}

// 保存语音配置并激活（密钥留空保留原值）
async function saveVoiceConfigAction() {
  if (voiceSaving.value) return
  if (voiceProvider.value === 'local_qwen' && !localVoiceUrl.value.trim()) {
    ElMessage.warning('请填写本地语音服务地址')
    return
  }
  voiceSaving.value = true
  try {
    const res = await saveVoiceConfig({
      provider: voiceProvider.value,
      ttsUrl: voiceProvider.value === 'local_qwen' ? localVoiceUrl.value.trim() : '',
      secretId: voiceForm.value.secretId.trim(),
      secretKey: voiceForm.value.secretKey.trim(),
      appId: voiceForm.value.appId.trim(),
      asrPrompt: voiceForm.value.asrPrompt.trim(),
    })
    if (res.code === 200) {
      ElMessage.success(
        voiceProvider.value === 'local_qwen'
          ? '已保存并激活：本地 Qwen 负责合成与识别，立即生效'
          : '语音配置已保存并激活，立即生效'
      )
      voiceMaskedId.value = res.data?.secretId || ''
      voiceForm.value.secretId = ''
      voiceForm.value.secretKey = ''
      voiceForm.value.appId = res.data?.appId || ''
      voiceForm.value.asrPrompt = res.data?.asrPrompt || ''
      if (res.data?.ttsUrl && voiceProvider.value === 'local_qwen') {
        localVoiceUrl.value = res.data.ttsUrl
      }
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

onMounted(async () => {
  fetchVoiceStatus()
  // 必须等语音配置回来再判断供应商：它决定下面要不要探本地服务
  await fetchVoiceConfig().catch(() => null)
  await fetchLocalProcess()
  const managed = Boolean(localProcess.value?.managed)
  // 已托管说明后端确实在跑本地 sidecar，即使供应商读出来还是旧值也要探
  if (voiceProvider.value !== 'local_qwen' && !managed) {
    return
  }
  await fetchLocalHealth().catch(() => null)
  const health = localHealth.value || {}
  // 后端已托管但模型还没加载完：开个短轮询，让标签自己变成"已加载"，
  // 而不是停在进页面那一刻的旧快照
  if (managed && !(health.reachable && health.loaded)) {
    startLocalPolling()
  }
})

onBeforeUnmount(stopLocalPolling)
</script>

<template>
  <el-card class="panel" shadow="never">
    <template #header>
      <span>语音服务配置（TTS/ASR）</span>
    </template>

    <el-form label-width="110px" class="voice-form">
      <el-form-item label="语音供应商">
        <el-radio-group v-model="voiceProvider">
          <el-radio-button value="tencent">腾讯云 API</el-radio-button>
          <el-radio-button value="local_qwen">本地 Qwen（TTS+ASR）</el-radio-button>
        </el-radio-group>
        <div class="hint">
          本地模式免密钥、无调用费用：Qwen3-TTS 负责面试官朗读，Qwen3-ASR 负责识别你的回答，
          两张模型跑在同一个 voice-service 进程里。后端启动时若供应商是本地 Qwen，会<strong>自动拉起</strong>
          voice-service；后端关闭时同步结束（只结束由后端拉起的那一个，手工启动的实例不会被误杀）。
        </div>
      </el-form-item>

      <template v-if="voiceProvider === 'local_qwen'">
        <el-form-item label="本地服务">
          <el-button type="primary" :loading="localStarting" @click="startLocalVoiceService">
            启动本地服务
          </el-button>
          <el-button
            :loading="localStopping"
            :disabled="!localProcess?.managed && !localHealth?.reachable"
            style="margin-left: 12px"
            @click="stopLocalVoiceService"
          >
            {{ localProcess?.managed ? '停止' : '强制停止' }}
          </el-button>
          <el-tag :type="localProcessTagType" style="margin-left: 12px">
            {{ localProcessText }}
          </el-tag>
          <div v-if="localProcess?.lastError" class="hint error-hint">
            上次启动失败：{{ localProcess.lastError }}
          </div>
          <div v-if="localProcess?.portListening && !localProcess?.managed" class="hint error-hint">
            端口 {{ localProcess?.baseUrl }} 上有服务在跑但不由后端托管（多半是上次后端崩溃遗留的孤儿，
            占着显存）。点「强制停止」可以回收；后端已经关掉时执行
            <code>pwsh -File voice-service/stop.ps1</code>。
          </div>
        </el-form-item>
        <el-form-item label="服务地址">
          <el-input v-model="localVoiceUrl" placeholder="http://127.0.0.1:9100" />
          <el-button :loading="localTesting" style="margin-left: 12px" @click="testLocalVoice">
            测试连通
          </el-button>
        </el-form-item>
        <el-form-item label="麦克风自检">
          <MicSelfTest />
        </el-form-item>
        <el-form-item label="识别热词">
          <el-input
            v-model="voiceForm.asrPrompt"
            type="textarea"
            :rows="3"
            :placeholder="asrPromptDefault || '留空则使用内置热词表'"
          />
          <div class="hint">
            本地识别会把这段文字作为提示偏置，减少同音错字。<strong>请按你希望“输出”的写法填写</strong>——
            大小写会被照抄：实测 <code>etcd</code> 纠正不了、写成 <code>ETCD</code> 才能识别对；
            不加时 <code>JVM</code> 会变成 <code>JYEM</code>、<code>gRPC</code> 会变成 <code>GRPC</code>。
            <strong>留空表示使用内置默认表</strong>；建议只填岗位高频术语，列表太长反而稀释偏置。
            <el-button link type="primary" size="small" @click="fillDefaultAsrPrompt">
              填入内置默认
            </el-button>
          </div>
        </el-form-item>
        <el-alert
          v-if="localHealth"
          :type="localHealth.reachable ? (localHealth.loaded ? 'success' : 'info') : 'warning'"
          :closable="false"
          style="margin-bottom: 12px"
        >
          <div v-if="!localHealth.reachable">
            无法连通 {{ localHealth.baseUrl || localVoiceUrl }}：{{ localHealth.error || '服务未启动' }}
          </div>
          <div v-else>
            已连通 · TTS <strong>{{ localHealth.model }}</strong> ·
            {{ localHealth.loaded ? '已加载' : '加载中' }} ·
            运行在 {{ localHealth.device }}
            <span v-if="localHealth.speakers?.length">· 音色：{{ localHealth.speakers.join(' / ') }}</span>
            <span v-if="localHealth.loadError">· 加载错误：{{ localHealth.loadError }}</span>
            <div v-if="localHealth.asrEnabled" style="margin-top: 4px">
              ASR <strong>{{ localHealth.asrModel }}</strong> ·
              {{ localHealth.asrLoaded ? '已加载' : '加载中' }} ·
              运行在 {{ localHealth.asrDevice }}
              <span v-if="localHealth.asrLanguage">· 语种：{{ localHealth.asrLanguage }}</span>
              <span v-else>· 语种：自动检测</span>
              <span v-if="localHealth.asrLoadError" class="error-hint">
                · 识别加载错误：{{ localHealth.asrLoadError }}
              </span>
            </div>
            <div v-else>ASR：本机已关闭本地识别（app.voice.local.asr.enabled=false）</div>
          </div>
        </el-alert>
        <el-alert
          v-if="localHealth?.reachable && localHealth?.asrEnabled === false"
          type="warning"
          :closable="false"
          style="margin-bottom: 12px"
        >
          本地识别已关闭：<strong>ASR（语音识别）仍走腾讯云</strong>，因此下面仍需保留腾讯云密钥。
        </el-alert>
        <el-alert v-else type="success" :closable="false" style="margin-bottom: 12px">
          TTS 与 ASR <strong>都走本地 Qwen</strong>，无需腾讯云密钥。下面的腾讯云配置仅在关闭本地识别、
          或本地服务异常触发回退时才会用到。
        </el-alert>
        <el-form-item v-if="localProcess?.logs?.length" label="最近日志">
          <el-collapse class="voice-log-collapse">
            <el-collapse-item
              :title="`展开查看 sidecar 输出（${localProcess.logs.length} 行，排错用）`"
              name="logs"
            >
              <pre class="voice-log">{{ localProcess.logs.join('\n') }}</pre>
            </el-collapse-item>
          </el-collapse>
        </el-form-item>
      </template>

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
        <span class="hint" v-if="voiceProvider === 'local_qwen'">
          TTS 与 ASR 均由本地 Qwen 提供，腾讯云配置可留空。
        </span>
        <span class="hint" v-else-if="voiceConfigured === false">
          请在下方填写腾讯云密钥并保存，或通过后端环境变量 TENCENT_SECRET_ID / TENCENT_SECRET_KEY 配置。
        </span>
        <span class="hint" v-else>STT / TTS 均由腾讯云提供。</span>
      </div>
      <div class="hint" v-if="voiceConfigured && !appIdConfigured && voiceProvider !== 'local_qwen'">
        提示：未配置 AppId，实时语音识别（WebSocket 流式）不可用，一句话识别不受影响。
      </div>
      <div class="hint">
        配置保存在本地数据库中（密钥仅脱敏回显，不会明文下发到浏览器），打包分发后直接在页面填写即可，无需修改环境变量。
      </div>
    </el-alert>
  </el-card>
</template>

<style scoped>
.panel {
  margin-bottom: 20px;
}
.hint {
  margin-left: 8px;
  color: #909399;
  font-size: 13px;
}
.error-hint {
  color: #f56c6c;
}
.voice-log {
  margin: 0;
  padding: 10px 12px;
  max-height: 220px;
  overflow: auto;
  background: #1e1e1e;
  color: #d4d4d4;
  border-radius: 4px;
  font-family: 'Courier New', monospace;
  font-size: 12px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-all;
  width: 100%;
}
.voice-log-collapse {
  width: 100%;
}
</style>
