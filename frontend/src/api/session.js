import request from './request'

// 查询某项目下的全部面试会话（笔试/模拟面试），按创建时间倒序
export const getSessionsByProject = (projectId) =>
  request.get(`/session/project/${projectId}`)
