import request from './request'
import axios from 'axios'

// STT：上传录音文件，返回识别文本
export const recognizeSpeech = (audioBlob) => {
  const formData = new FormData()
  formData.append('file', audioBlob, 'recording.webm')
  return request.post('/voice/stt', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
}

// TTS：文本合成语音，返回 audio/wav 二进制 Blob。
// 后端该接口直接返回二进制（不走 {code,message,data}），
// 故用独立 axios 实例 + responseType blob，避免拦截器解包破坏数据。
export const synthesizeSpeech = async (text, voiceType) => {
  const res = await axios.post('/api/voice/tts', { text, voiceType }, { responseType: 'blob' })
  return res.data
}

// 腾讯云语音密钥配置状态（不暴露密钥本身）
export const getVoiceStatus = () => request.get('/voice/status')

// 本地 Qwen 语音服务（voice-service sidecar）连通性与模型状态
export const getLocalVoiceHealth = () => request.get('/voice/local/health')

// sidecar 托管状态（是否由后端拉起、PID、最近日志）
export const getLocalVoiceProcess = () => request.get('/voice/local/process')

// 启动 sidecar（幂等；外部已在运行时不接管）
export const startLocalVoice = () => request.post('/voice/local/start')

/**
 * 结束 sidecar 并等后端确认真把端口关掉。
 * @param {boolean} force 后端崩过、pid 记录丢了却仍占显存时用：
 *   只有 /health 自报的工作目录就是本项目的 voice-service 才会结束它
 */
export const stopLocalVoice = (force = false) =>
  request.post('/voice/local/stop', null, { params: { force } })

// 获取当前生效的语音配置（脱敏回显）
export const getVoiceConfig = () => request.get('/voice/config')

// 保存语音配置并激活（密钥留空表示保留原值）
export const saveVoiceConfig = (data) => request.post('/voice/config', data)

// 保存候选人录音（wav），返回可回放的相对路径（用于消息 audioPath）
export const uploadAudio = (sessionId, audioBlob) => {
  const formData = new FormData()
  formData.append('file', audioBlob, 'recording.wav')
  return request.post(`/mock-interview/${sessionId}/audio`, formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
}
