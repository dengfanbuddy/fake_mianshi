import request from './request'

/** 错题统计概览 */
export const getReviewOverview = (projectId) =>
  request.get('/review/overview', { params: { projectId } })

/**
 * 错题清单
 * @param {number} projectId
 * @param {{ status?: 'all'|'pending'|'corrected', knowledgePoint?: string }} options
 */
export const getWrongQuestions = (projectId, options = {}) =>
  request.get('/review/wrong-questions', {
    params: {
      projectId,
      status: options.status && options.status !== 'all' ? options.status : undefined,
      knowledgePoint: options.knowledgePoint || undefined,
    },
  })

/** 重练一道错题（返回判分结果） */
export const retryWrongQuestion = (questionId, userAnswer) =>
  request.post(`/review/wrong-questions/${questionId}`, { userAnswer })

/** 项目下的薄弱知识点（与项目详情页共用同一接口） */
export const getWeaknessTags = (projectId) => request.get(`/weakness-tag/project/${projectId}`)
