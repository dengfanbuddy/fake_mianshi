import request from './request'

// 面试官风格列表
export const getPersonaList = () => request.get('/persona')

// 创建自定义风格
export const createPersona = (data) => request.post('/persona', data)

// 更新自定义风格
export const updatePersona = (id, data) => request.put(`/persona/${id}`, data)

// 删除自定义风格
export const deletePersona = (id) => request.delete(`/persona/${id}`)
