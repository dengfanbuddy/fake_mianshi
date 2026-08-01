import request from './request'

// 开始模拟面试
export const startMockInterview = (projectId, data) =>
  request.post(`/mock-interview/start/${projectId}`, data)

// 候选人回复一轮
export const respondMockInterview = (sessionId, data) =>
  request.post(`/mock-interview/respond/${sessionId}`, data)

// 主动收尾面试
export const concludeMockInterview = (sessionId) =>
  request.post(`/mock-interview/conclude/${sessionId}`)

// 获取会话全部消息
export const getMockInterviewMessages = (sessionId) =>
  request.get(`/mock-interview/messages/${sessionId}`)

// 中途切换面试官人设
export const switchPersona = (sessionId, newPersonaId) =>
  request.put(`/mock-interview/switch-persona/${sessionId}`, { newPersonaId })

// 面试官风格列表
export const getPersonaList = () => request.get('/persona')
