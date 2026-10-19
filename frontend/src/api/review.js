import request from './request'

/** 错题统计概览 */
export const getReviewOverview = (projectId) =>
  request.get('/review/overview', { params: { projectId } })

/**
 * 错题清单
 * @param {number} projectId
 * @param {{ status?: 'all'|'pending'|'corrected', knowledgePoint?: string, tagId?: number }} options
 *   tagId 优先：后端会带上该弱点标签的全部同义写法一起匹配
 */
export const getWrongQuestions = (projectId, options = {}) =>
  request.get('/review/wrong-questions', {
    params: {
      projectId,
      status: options.status && options.status !== 'all' ? options.status : undefined,
      knowledgePoint: options.knowledgePoint || undefined,
      tagId: options.tagId || undefined,
    },
  })

/** 重练一道错题（返回判分结果） */
export const retryWrongQuestion = (questionId, userAnswer) =>
  request.post(`/review/wrong-questions/${questionId}`, { userAnswer })

/** 项目下的薄弱知识点（同义写法已归并，aliases 里是其余写法） */
export const getWeaknessTags = (projectId) => request.get(`/weakness-tag/project/${projectId}`)

/** 手动归并同义弱点标签（幂等，启动时已自动跑过一次） */
export const consolidateWeaknessTags = (projectId) =>
  request.post(`/weakness-tag/project/${projectId}/consolidate`)
