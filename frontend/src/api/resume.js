import request from './request'

// 上传并解析简历 PDF（multipart）
export const uploadResume = (projectId, file) => {
  const formData = new FormData()
  formData.append('file', file)
  return request.post(`/resume/upload/${projectId}`, formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
}

// 获取某项目下最新简历
export const getResume = (projectId) => request.get(`/resume/${projectId}`)

// 调用 LLM 分析简历（耗时较长）
export const analyzeResume = (resumeId) => request.post(`/resume/analyze/${resumeId}`)
