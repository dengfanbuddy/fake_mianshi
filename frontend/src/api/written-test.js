import request from './request'
export const startTest = (projectId, data) => request.post(`/written-test/start/${projectId}`, data)
export const submitTest = (sessionId, answers) => request.post(`/written-test/submit/${sessionId}`, { answers })
export const getTestDetail = (sessionId) => request.get(`/written-test/${sessionId}`)
