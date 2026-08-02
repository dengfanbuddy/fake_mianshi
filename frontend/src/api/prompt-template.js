import request from './request'

// 提示词模板列表（occupation 可选；缺省返回全部）
export const getPromptTemplates = (occupation) =>
  request.get('/prompt-template', { params: occupation ? { occupation } : {} })

// 已配置职业列表
export const getPromptOccupations = () => request.get('/prompt-template/occupations')

// 保存/编辑模板
export const savePromptTemplate = (data) => request.put('/prompt-template', data)

// AI 针对职业重新生成某场景模板
export const regeneratePromptTemplate = (data) => request.post('/prompt-template/regenerate', data)

// 检测/补齐某职业的模板（新职业时 AI 生成）
export const ensurePromptTemplates = (occupation) =>
  request.post(`/prompt-template/ensure/${encodeURIComponent(occupation)}`)
