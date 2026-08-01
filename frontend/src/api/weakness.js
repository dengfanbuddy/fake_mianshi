import request from './request'

// 查询某项目下的全部薄弱知识点标签
export const getWeaknessTags = (projectId) =>
  request.get(`/weakness-tag/project/${projectId}`)
