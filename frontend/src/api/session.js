import request from './request'

// 查询某项目下的全部面试会话（笔试/模拟面试），按创建时间倒序
export const getSessionsByProject = (projectId) =>
  request.get(`/session/project/${projectId}`)

// 查询单个会话信息（含 projectId，用于报告页返回所属项目）
export const getSessionInfo = (sessionId) => request.get(`/session/${sessionId}`)

// 看板统计：项目数 / 会话数 / 笔试数 / 面试数
export const getDashboardStats = () => request.get('/session/stats')

// 删除单个会话（含子数据与录音文件）
export const deleteSession = (sessionId) => request.delete(`/session/${sessionId}`)
