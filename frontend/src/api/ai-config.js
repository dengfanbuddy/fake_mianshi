import request from './request'

// AI 模型配置列表
export const getAiConfigs = () => request.get('/ai-config')

// 新增或更新 AI 模型配置
export const saveAiConfig = (data) => request.post('/ai-config', data)

// 将指定配置设为启用
export const setActiveAiConfig = (id) => request.put(`/ai-config/${id}/active`)
