import request from './request'
import axios from 'axios'

// 获取单次会话 AI 分析报告
export const getSessionAnalysis = (sessionId) => request.get(`/analysis/session/${sessionId}`)

// 历史综合分析报告
export const getHistoryAnalysis = (projectId) => request.get(`/analysis/history/${projectId}`)

// 历史综合分析状态（未分析会话数 / 是否有新分析可重新综合）
export const getHistoryStatus = (projectId) => request.get(`/analysis/history/status/${projectId}`)

// 强制重新生成历史综合分析
export const refreshHistoryAnalysis = (projectId) => request.post(`/analysis/history/refresh/${projectId}`)

// 笔试原始结果（用于报告补充展示）
export const getWrittenTestDetail = (sessionId) => request.get(`/written-test/${sessionId}`)

// 面试对话记录（用于报告回看）
export const getMockMessages = (sessionId) => request.get(`/mock-interview/messages/${sessionId}`)

// 强制重新生成分析报告
export const refreshSessionAnalysis = (sessionId) => request.post(`/analysis/refresh/${sessionId}`)

// 静默探测会话类型：笔试返回 data，模拟面试（404）返回 null；不触发全局错误提示
export const probeWrittenTestDetail = async (sessionId) => {
  try {
    const res = await axios.get(`/api/written-test/${sessionId}`)
    return res?.data?.data || null
  } catch (e) {
    return null
  }
}
