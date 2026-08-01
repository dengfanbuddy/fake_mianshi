import request from './request'

// 项目列表（按创建时间倒序）
export const getProjects = () => request.get('/project')

// 项目详情
export const getProject = (id) => request.get(`/project/${id}`)

// 新建项目
export const createProject = (data) => request.post('/project', data)

// 更新项目
export const updateProject = (id, data) => request.put(`/project/${id}`, data)

// 删除项目
export const deleteProject = (id) => request.delete(`/project/${id}`)
