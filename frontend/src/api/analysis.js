import request from './request'

// 获取单次会话 AI 分析报告
export const getSessionAnalysis = (sessionId) => request.get(`/analysis/session/${sessionId}`)

// 获取历史综合分析报告
export const getHistoryAnalysis = (projectId) => request.get(`/analysis/history/${projectId}`)

// 笔试原始结果（用于报告补充展示）
export const getWrittenTestDetail = (sessionId) => request.get(`/written-test/${sessionId}`)

// 面试对话记录（用于报告回看）
export const getMockMessages = (sessionId) => request.get(`/mock-interview/messages/${sessionId}`)
