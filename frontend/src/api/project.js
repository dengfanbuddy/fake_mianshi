import request from './request'

// 项目列表（按创建时间倒序）
export const getProjects = () => request.get('/project')

// 项目列表分页（page 从 1 开始）；返回 { records, total, page, size, totalPages }
export const getProjectsPage = (page = 1, size = 20) =>
  request.get('/project/page', { params: { page, size } })

// 项目详情
export const getProject = (id) => request.get(`/project/${id}`)

// 新建项目
export const createProject = (data) => request.post('/project', data)

// 更新项目
export const updateProject = (id, data) => request.put(`/project/${id}`, data)

// 删除项目
export const deleteProject = (id) => request.delete(`/project/${id}`)
