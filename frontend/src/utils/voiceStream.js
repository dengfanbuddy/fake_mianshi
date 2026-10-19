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
  /** 上一帧拆样本剩下的半个样本（奇数字节）。丢掉它会让自己之后的所有样本错位一字节 →
   *  int16 被解释成噪声，听起来是突然的白噪音；所以必须跨帧拼回来。 */
  let carry = null

  function toInt16(arrayBuffer) {
    const incoming = new Uint8Array(arrayBuffer)
    let buf = incoming
    if (carry) {
      buf = new Uint8Array(carry.length + incoming.length)
      buf.set(carry, 0)
      buf.set(incoming, carry.length)
      carry = null
    }
    let usable = buf.length - (buf.length % 2)
    if (usable !== buf.length) {
      carry = buf.slice(usable)
    }
    if (usable === 0) return null
    if (buf.byteOffset % 2 === 0) {
      return new Int16Array(buf.buffer, buf.byteOffset, usable / 2)
    }
    const aligned = new Uint8Array(usable)
    aligned.set(buf.subarray(0, usable))
    return new Int16Array(aligned.buffer)
  }

  function connect(timeoutMs = 8000) {
    return new Promise((resolve, reject) => {
      let settled = false
      carry = null
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
            const pcm = toInt16(ev.data)
            if (pcm && pcm.length && onAudio) onAudio(pcm)
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
