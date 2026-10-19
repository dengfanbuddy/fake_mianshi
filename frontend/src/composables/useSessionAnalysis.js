import { ref, computed, onBeforeUnmount } from 'vue'
import { getSessionAnalysis } from '../api/analysis'

// 生成总超时（秒）
const GENERATE_TIMEOUT = 420
// 空闲超时（秒）：超过该时长无任何输出则判定中断
const IDLE_TIMEOUT = 60

/**
 * 分析报告的「数据获取 + 生成状态机」。
 *
 * <p>把原 SessionReport.vue 里的三套获取路径收敛到一处：
 * 一次性拉取、SSE 流式生成（失败/8 秒无输出回落轮询）、轮询兜底。
 * 组件卸载时统一由 dispose() 关闭 EventSource 与定时器。
 *
 * @param {string|number} sessionId 会话 ID
 */
export function useSessionAnalysis(sessionId) {
  const analysis = ref(null)

  // 分析生成中：SSE 流式展示（生成中直接显示原文，完成后格式化），失败回落轮询
  const generating = ref(false)
  const generateElapsed = ref(0)
  const generateTimedOut = ref(false)
  const streamText = ref('') // AI 正式输出内容（原文实时显示）
  const streamReasoning = ref('') // 思考型模型的思考过程（展示"思考中"状态）
  const failReason = ref('') // 生成失败/中断原因（SSE error 或超时）
  const refreshing = ref(false)

  const generatingTitle = computed(() => {
    if (streamText.value) return 'AI 正在生成分析报告，边写边展示…'
    if (streamReasoning.value) return 'AI 正在思考，即将开始输出…'
    return 'AI 正在准备分析报告…'
  })

  const stalled = ref(false) // 输出停滞提示
  let generateTimer = null
  let eventSource = null
  let streamDone = false // SSE 已收到 done（避免 error 事件误触发轮询）

  /** 拉取一次分析；成功返回 true 并填充数据 */
  async function fetchAnalysisOnce() {
    try {
      const res = await getSessionAnalysis(sessionId)
      if (res.code === 200 && res.data) {
        analysis.value = res.data
        return true
      }
      return false
    } catch (e) {
      // 拦截器已提示（分析不存在时后端返回成功+null，不走异常）
      return false
    }
  }

  /** 分析未生成时打开 SSE 流：实时渲染 AI 生成的分析内容，done 后展示完整报告；force=true 时强制重新生成 */
  function startStreamingAnalysis(force = false) {
    generating.value = true
    generateElapsed.value = 0
    generateTimedOut.value = false
    streamDone = false
    streamText.value = ''
    streamReasoning.value = ''
    failReason.value = ''
    if (eventSource) eventSource.close()
    // 超时策略：空闲超时（无任何输出 60 秒）+ 总超时（300 秒），有输出则持续等待
    let lastOutputAt = Date.now()
    let lastLen = streamText.value.length
    let stallCheck = 0
    stalled.value = false
    if (generateTimer) clearInterval(generateTimer)
    generateTimer = setInterval(() => {
      generateElapsed.value += 1
      if (streamDone) return
      // 30 秒字数无增长 → 提示停滞（不判死，继续等）
      if (streamText.value.length === lastLen) {
        stallCheck += 1
        if (stallCheck >= 30) stalled.value = true
      } else {
        lastLen = streamText.value.length
        stallCheck = 0
        stalled.value = false
      }
      // SSE 连接后 8 秒仍无任何输出（delta/reasoning）→ 自动切轮询兜底，保证有反馈
      if (generateElapsed.value >= 8 && !streamDone && !streamText.value && !streamReasoning.value) {
        stopStreamingAnalysis()
        startGeneratePolling()
        return
      }
      if (Date.now() - lastOutputAt > IDLE_TIMEOUT * 1000) {
        stopStreamingAnalysis()
        generateTimedOut.value = true
        return
      }
      if (generateElapsed.value >= GENERATE_TIMEOUT) {
        stopStreamingAnalysis()
        generateTimedOut.value = true
      }
    }, 1000)

    try {
      const url = force
        ? `/api/analysis/stream/${sessionId}?force=1`
        : `/api/analysis/stream/${sessionId}`
      eventSource = new EventSource(url)
      eventSource.addEventListener('reasoning', (e) => {
        if (e.data) {
          streamReasoning.value += e.data
          lastOutputAt = Date.now()
        }
      })
      eventSource.addEventListener('delta', (e) => {
        if (e.data) {
          streamText.value += e.data
          lastOutputAt = Date.now()
        }
      })
      eventSource.addEventListener('done', (e) => {
        streamDone = true
        try {
          const dto = JSON.parse(e.data)
          if (dto) {
            analysis.value = dto
            stopStreamingAnalysis()
            return
          }
        } catch (err) {
          // data 解析失败，回落轮询
        }
        stopStreamingAnalysis()
        startGeneratePolling()
      })
      eventSource.addEventListener('error', (e) => {
        // 服务器推送的错误（带 data）：显示具体原因
        if (e.data) {
          failReason.value = e.data
          stopStreamingAnalysis()
          generateTimedOut.value = true
          return
        }
        // done 已收到时连接关闭是正常现象；否则（连接失败/中断）回落轮询
        if (!streamDone && !analysis.value) {
          stopStreamingAnalysis()
          if (!generateTimedOut.value) startGeneratePolling()
        }
      })
    } catch (e) {
      stopStreamingAnalysis()
      startGeneratePolling()
    }
  }

  function stopStreamingAnalysis() {
    if (generateTimer) {
      clearInterval(generateTimer)
      generateTimer = null
    }
    if (eventSource) {
      eventSource.close()
      eventSource = null
    }
    generating.value = false
  }

  /** 分析未生成时轮询等待，每 5 秒一次，超时后停止并提示 */
  function startGeneratePolling() {
    generating.value = true
    generateElapsed.value = 0
    generateTimedOut.value = false
    if (generateTimer) clearInterval(generateTimer)
    generateTimer = setInterval(async () => {
      generateElapsed.value += 5
      if (generateElapsed.value >= GENERATE_TIMEOUT) {
        stopGeneratePolling()
        generateTimedOut.value = true
        return
      }
      const ok = await fetchAnalysisOnce()
      if (ok) {
        stopGeneratePolling()
      }
    }, 5000)
  }

  function stopGeneratePolling() {
    if (generateTimer) {
      clearInterval(generateTimer)
      generateTimer = null
    }
    generating.value = false
  }

  // 重新生成分析报告（流式展示生成过程）
  function handleRefresh() {
    refreshing.value = true
    // 清空当前展示，进入流式重新生成（force=1）
    stopGeneratePolling()
    analysis.value = null
    streamText.value = ''
    streamReasoning.value = ''
    startStreamingAnalysis(true)
    refreshing.value = false
  }

  /** 卸载清理：关闭 EventSource 与所有定时器 */
  function dispose() {
    stopStreamingAnalysis()
    stopGeneratePolling()
  }

  // 与原组件一致：卸载时释放 SSE 连接与定时器
  onBeforeUnmount(dispose)

  return {
    analysis,
    generating,
    generateElapsed,
    generateTimedOut,
    stalled,
    streamText,
    streamReasoning,
    failReason,
    refreshing,
    generatingTitle,
    GENERATE_TIMEOUT,
    IDLE_TIMEOUT,
    fetchAnalysisOnce,
    startStreamingAnalysis,
    stopStreamingAnalysis,
    startGeneratePolling,
    stopGeneratePolling,
    handleRefresh,
    dispose,
  }
}
