<script setup>
import { ref, computed, onBeforeUnmount } from 'vue'
import { PcmRecorder } from '../utils/pcmRecorder'

/**
 * 按住说话录音组件（16k PCM 直采）。
 * - pointerdown 开始录音，pointerup / pointerleave 结束（贴近真实对讲）
 * - 录音过程中持续 emit('chunk', Int16Array) 供流式 ASR
 * - 结束后 emit('recorded', wavBlob) 供回退 HTTP 路径
 */
const emit = defineEmits(['recorded', 'error', 'start', 'stop', 'chunk'])

const MAX_DURATION = 120 // 秒

const state = ref('idle') // idle | recording
const seconds = ref(0)

let recorder = null
let timer = null
let startTime = 0
let unmounted = false
let pending = false
let stopRequested = false

const isSupported =
  typeof navigator !== 'undefined' &&
  !!navigator.mediaDevices?.getUserMedia &&
  !!(window.AudioContext || window.webkitAudioContext)

const isRecording = computed(() => state.value === 'recording')

const displayTime = computed(() => {
  const m = Math.floor(seconds.value / 60)
    .toString()
    .padStart(2, '0')
  const s = (seconds.value % 60).toString().padStart(2, '0')
  return `${m}:${s}`
})

async function startRecording() {
  if (state.value === 'recording' || pending) return
  if (!isSupported) {
    ElMessage.warning('当前浏览器不支持录音，请改用文字输入')
    emit('error', 'unsupported')
    return
  }
  pending = true
  try {
    recorder = new PcmRecorder({
      onChunk: (int16) => {
        emit('chunk', int16)
      },
      onError: (e) => {
        ElMessage.warning(`录音出错：${e?.message || '未知错误'}`)
        emit('error', 'recorder-error')
      },
    })
    await recorder.start()
    pending = false
    // 用户在权限请求期间已松开按钮，直接放弃本次录音
    if (unmounted || stopRequested) {
      stopRequested = false
      recorder.stop()
      recorder = null
      return
    }
    state.value = 'recording'
    startTime = Date.now()
    seconds.value = 0
    timer = setInterval(() => {
      seconds.value = Math.floor((Date.now() - startTime) / 1000)
      if (seconds.value >= MAX_DURATION) {
        ElMessage.info('已达最长录音时长，已自动停止')
        stopRecording()
      }
    }, 500)
    emit('start')
  } catch (e) {
    pending = false
    stopRequested = false
    if (e.name === 'NotAllowedError' || e.name === 'PermissionDeniedError' || e.name === 'SecurityError') {
      ElMessage.warning('麦克风权限被拒绝，请授权后重试，或改用文字输入')
    } else {
      ElMessage.warning(`无法访问麦克风：${e.message || '未知错误'}，请改用文字输入`)
    }
    if (recorder) {
      recorder.stop()
      recorder = null
    }
    emit('error', 'permission-denied')
  }
}

function requestStop() {
  if (state.value === 'recording') {
    stopRecording()
  } else if (pending) {
    // 录音尚未真正开始，标记待停止
    stopRequested = true
  }
}

function stopRecording() {
  if (state.value !== 'recording' || !recorder) return
  if (timer) {
    clearInterval(timer)
    timer = null
  }
  state.value = 'idle'
  emit('stop')

  let wavBlob = null
  let failed = false
  try {
    wavBlob = recorder.getWavBlob()
  } catch (e) {
    failed = true
  }
  // 无论取 blob 是否成功，都必须释放麦克风轨道
  try {
    recorder.stop()
  } catch (e) {
    failed = true
  }
  recorder = null

  // 关键：任何结束路径都必须发 recorded（异常时发 null）。
  // 否则父组件的 recording 状态会永久卡在 true，把输入框和发送按钮锁死，
  // 用户只能刷新页面才能恢复。
  if (failed) {
    ElMessage.error('录音结束处理失败，请重试或改用文字输入')
    emit('error', 'recorder-error')
    if (!unmounted) emit('recorded', null)
    return
  }
  if (!wavBlob || wavBlob.size <= 44) {
    // 录音时长过短（仅 WAV 头，无音频帧）：提示后同样要复位状态
    ElMessage.warning('录音时长过短，请按住按钮说完后再松开')
    if (!unmounted) emit('recorded', null)
    return
  }
  if (!unmounted) emit('recorded', wavBlob)
}

function cleanup() {
  if (timer) {
    clearInterval(timer)
    timer = null
  }
  if (recorder) {
    recorder.stop()
    recorder = null
  }
}

onBeforeUnmount(() => {
  unmounted = true
  cleanup()
})
</script>

<template>
  <div class="audio-recorder">
    <button
      class="record-btn"
      :class="{ recording: isRecording }"
      :title="isRecording ? '松开结束录音' : '按住说话'"
      @pointerdown.prevent="startRecording"
      @pointerup.prevent="requestStop"
      @pointerleave="requestStop"
      @pointercancel="requestStop"
      @contextmenu.prevent
    >
      <span class="dot" v-if="isRecording" />
      <svg
        v-else
        viewBox="0 0 24 24"
        width="20"
        height="20"
        fill="currentColor"
        aria-hidden="true"
      >
        <path d="M12 14a3 3 0 0 0 3-3V6a3 3 0 0 0-6 0v5a3 3 0 0 0 3 3zm5.5-3a5.5 5.5 0 0 1-11 0H4a8 8 0 0 0 7 7.94V22h2v-3.06A8 8 0 0 0 20 11h-2.5z" />
      </svg>
      <span v-if="isRecording" class="time">{{ displayTime }}</span>
    </button>
    <span class="hint" :class="{ recording: isRecording }">
      {{ isRecording ? '松开结束' : '按住说话' }}
    </span>
  </div>
</template>

<style scoped>
.audio-recorder {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  flex-shrink: 0;
}
.record-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  width: 48px;
  height: 48px;
  border-radius: 50%;
  border: none;
  background: #ffffff;
  color: #409eff;
  cursor: pointer;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.12);
  transition: all 0.2s;
  user-select: none;
  -webkit-user-select: none;
  touch-action: none;
}
.record-btn:hover {
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.16);
  transform: scale(1.04);
}
.record-btn:active {
  transform: scale(0.96);
}
.record-btn.recording {
  background: #f56c6c;
  color: #fff;
}
.dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: #fff;
  animation: pulse 1s ease-in-out infinite;
}
.time {
  font-size: 13px;
  font-variant-numeric: tabular-nums;
  font-weight: 600;
  letter-spacing: 0.5px;
}
.hint {
  font-size: 11px;
  color: #909399;
  transition: color 0.2s;
}
.hint.recording {
  color: #f56c6c;
  font-weight: 600;
}
@keyframes pulse {
  0% {
    box-shadow: 0 0 0 0 rgba(255, 255, 255, 0.7);
  }
  70% {
    box-shadow: 0 0 0 12px rgba(255, 255, 255, 0);
  }
  100% {
    box-shadow: 0 0 0 0 rgba(255, 255, 255, 0);
  }
}
</style>
