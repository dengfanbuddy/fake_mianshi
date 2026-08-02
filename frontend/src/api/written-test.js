import request from './request'
// 开始考试为 AI 长任务（出题），支持外部中止信号（前端超时重试用）
export const startTest = (projectId, data, signal) =>
  request.post(`/written-test/start/${projectId}`, data, { signal, timeout: 120000 })
export const submitTest = (sessionId, answers) => request.post(`/written-test/submit/${sessionId}`, { answers })
export const getTestDetail = (sessionId) => request.get(`/written-test/${sessionId}`)
// 继续进行中的笔试：返回考试内容（题目脱敏）
export const getExamInProgress = (sessionId) => request.get(`/written-test/exam/${sessionId}`)
