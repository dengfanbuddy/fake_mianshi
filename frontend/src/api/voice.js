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
