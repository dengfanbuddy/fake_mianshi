/**
 * 面试语音流式 WebSocket 客户端（Phase B）。
 * 与后端 /api/ws/interview 交互：start → 推 PCM → end；接收 ready/partial/text/done/error 事件与二进制音频帧。
 */
export function createVoiceStream({ sessionId, onReady, onPartial, onUserText, onText, onAudio, onDone, onError, onSttEmpty }) {
  const protocol = window.location.protocol === 'https:' ? 'wss' : 'ws'
  const url = `${protocol}://${window.location.host}/api/ws/interview`

  let ws = null
  let started = false
  let closedByClient = false

  function connect(timeoutMs = 8000) {
    return new Promise((resolve, reject) => {
      let settled = false
      const fail = (msg) => {
        if (settled) return
        settled = true
        try { ws && ws.close() } catch { /* ignore */ }
        reject(new Error(msg))
      }
      try {
        ws = new WebSocket(url)
      } catch (e) {
        fail(`WebSocket 创建失败: ${e.message}`)
        return
      }
      ws.binaryType = 'arraybuffer'
      const timer = setTimeout(() => fail('WebSocket 连接超时'), timeoutMs)

      ws.onopen = () => {
        try {
          ws.send(JSON.stringify({ type: 'start', sessionId }))
        } catch (e) {
          fail(`发送失败: ${e.message}`)
          clearTimeout(timer)
        }
      }
      ws.onmessage = (ev) => {
        if (typeof ev.data === 'string') {
          let msg
          try { msg = JSON.parse(ev.data) } catch { return }
          const t = msg.type
          if (t === 'ready') {
            started = true
            settled = true
            clearTimeout(timer)
            resolve()
            onReady && onReady()
          } else if (t === 'partial') {
            onPartial && onPartial(msg.data?.text || '')
          } else if (t === 'user_text') {
            onUserText && onUserText(msg.data)
          } else if (t === 'text') {
            onText && onText(msg.data?.delta || '')
          } else if (t === 'done') {
            onDone && onDone(msg.data)
          } else if (t === 'error') {
            onError && onError(msg.data?.message || '流式语音出错')
          } else if (t === 'stt_empty') {
            onSttEmpty && onSttEmpty()
          }
        } else {
          // 二进制帧：面试官语音 PCM
          if (ev.data instanceof ArrayBuffer) {
            onAudio && onAudio(new Int16Array(ev.data))
          }
        }
      }
      ws.onerror = () => {
        if (!started) fail('WebSocket 连接错误')
        else onError && onError('WebSocket 连接错误')
      }
      ws.onclose = () => {
        if (!started && !settled) fail('WebSocket 连接关闭')
      }
    })
  }

  return {
    connect,
    push(int16) {
      if (ws && ws.readyState === WebSocket.OPEN && int16 && int16.length) {
        ws.send(int16.buffer)
      }
    },
    end() {
      if (ws && ws.readyState === WebSocket.OPEN) {
        ws.send(JSON.stringify({ type: 'end' }))
      }
    },
    close() {
      closedByClient = true
      if (ws) {
        try { ws.close() } catch { /* ignore */ }
        ws = null
      }
    },
    get isStarted() {
      return started
    },
  }
}
