import request from './request'

// 查询某项目的职位需求（无则 data=null）
export const getPosition = (projectId) => request.get(`/position/${projectId}`)

// 保存或更新某项目的职位需求
export const savePosition = (projectId, data) => request.post(`/position/${projectId}`, data)

// 从最新简历分析结果生成职位需求
export const generatePositionFromResume = (projectId) =>
  request.post(`/position/${projectId}/from-resume`)
