import request from './request'

// 开始模拟面试
export const startMockInterview = (projectId, data) =>
  request.post(`/mock-interview/start/${projectId}`, data)

// 候选人回复一轮
export const respondMockInterview = (sessionId, data) =>
  request.post(`/mock-interview/respond/${sessionId}`, data)

// 主动收尾面试
export const concludeMockInterview = (sessionId) =>
  request.post(`/mock-interview/conclude/${sessionId}`)

// 获取会话全部消息
export const getMockInterviewMessages = (sessionId) =>
  request.get(`/mock-interview/messages/${sessionId}`)

// 中途切换面试官人设
export const switchPersona = (sessionId, newPersonaId) =>
  request.put(`/mock-interview/switch-persona/${sessionId}`, { newPersonaId })

// 面试官风格列表
export const getPersonaList = () => request.get('/persona')

// 流式回复：POST + SSE，逐事件回调 { event, data }。
// event ∈ delta / reasoning / done / error
export const respondMockInterviewStream = async (sessionId, data, onEvent) => {
  const res = await fetch(`/api/mock-interview/respond-stream/${sessionId}`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(data || {}),
  })
  if (!res.ok || !res.body) {
    let msg = `HTTP ${res.status}`
    try { msg = (await res.json())?.message || msg } catch { /* ignore */ }
    throw new Error(msg)
  }
  const reader = res.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''
  while (true) {
    const { done, value } = await reader.read()
    if (done) break
    buffer += decoder.decode(value, { stream: true })
    let idx
    while ((idx = buffer.indexOf('\n\n')) >= 0) {
      const raw = buffer.slice(0, idx)
      buffer = buffer.slice(idx + 2)
      const evt = parseSse(raw)
      if (evt) onEvent(evt)
    }
  }
}

function parseSse(raw) {
  let event = 'message'
  const dataLines = []
  for (const line of raw.split('\n')) {
    if (line.startsWith('event:')) event = line.slice(6).trim()
    else if (line.startsWith('data:')) dataLines.push(line.slice(5).trimStart())
  }
  if (!dataLines.length) return null
  const data = dataLines.join('\n')
  let parsed
  try { parsed = JSON.parse(data) } catch { parsed = data }
  return { event, data: parsed }
}
