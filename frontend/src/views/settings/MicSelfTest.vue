<script setup>
/**
 * 麦克风自检。
 *
 * 存在的意义：识别失败最常见的原因不是模型，而是麦克风根本没送数据
 * （实测录到的 wav 精确全零，peak=0）。这个自检把「麦克风问题」和「识别不准」区分开，
 * 用户不必靠猜。
 */
import { ref, onBeforeUnmount } from 'vue'
import { PcmRecorder } from '../../utils/pcmRecorder'

const testing = ref(false)
const peak = ref(0)
const result = ref(null) // null | { type, text }
let recorder = null

async function testMic() {
  if (testing.value) return
  if (!navigator.mediaDevices?.getUserMedia) {
    ElMessage.warning('当前浏览器不支持录音')
    return
  }
  testing.value = true
  peak.value = 0
  result.value = null
  try {
    recorder = new PcmRecorder({
      onLevel: (v) => {
        if (v > peak.value) peak.value = v
      },
    })
    // 只要 start() 没抛异常就说明麦克风被成功打开了（静音会被探测逻辑标记）
    await recorder.start()
    recorder.resume()
    ElMessage.info('请对着麦克风说一句话…')
    await new Promise((r) => setTimeout(r, 2000))
    const v = peak.value
    if (v <= 0) {
      result.value = {
        type: 'danger',
        text: '麦克风没有输出任何声音（全零）。检查输入设备、静音键、系统麦克风权限',
      }
    } else if (v < 0.05) {
      result.value = { type: 'warning', text: `电平偏低（${Math.round(v * 100)}%），靠近麦克风或调高输入音量` }
    } else {
      result.value = { type: 'success', text: `麦克风正常（电平 ${Math.round(v * 100)}%）` }
    }
  } catch (e) {
    result.value = {
      type: 'danger',
      text: `无法打开麦克风：${e?.name === 'NotAllowedError' ? '权限被拒绝' : e?.message || '未知错误'}`,
    }
  } finally {
    if (recorder) {
      try { recorder.stop() } catch { /* ignore */ }
      recorder = null
    }
    testing.value = false
  }
}

onBeforeUnmount(() => {
  if (recorder) {
    try { recorder.stop() } catch { /* ignore */ }
    recorder = null
  }
})
</script>

<template>
  <div class="mic-self-test">
    <el-button :loading="testing" @click="testMic">检测 2 秒</el-button>
    <el-tag v-if="result" :type="result.type" style="margin-left: 12px">{{ result.text }}</el-tag>
    <div class="hint">
      「识别不出内容」最常见的原因其实是<strong>麦克风没送数据</strong>（录到的音频精确全零）。
      点检测后说一句话，这里会报峰值电平，用来区分「麦克风问题」和「识别不准」。
      录音时按钮下方也有实时电平条，条不动就是没采到声音。
    </div>
  </div>
</template>

<style scoped>
.mic-self-test {
  width: 100%;
}
.hint {
  margin-left: 0;
  margin-top: 6px;
  color: #909399;
  font-size: 13px;
}
</style>
