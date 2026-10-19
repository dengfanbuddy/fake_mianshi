/**
 * 模拟面试会话编排：会话状态、面试官风格列表、开始面试、结束面试、刷新后恢复历史消息。
 * 消息数组、当前人设等派生状态也在这里，父组件与回复链路共用同一个 messages 引用。
 */
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  startMockInterview,
  concludeMockInterview,
  getMockInterviewMessages,
  getPersonaList,
} from '../api/mock-interview'

export function useInterviewSession() {
  const route = useRoute()
  const router = useRouter()

  // ---------- 会话状态 ----------
  const projectId = ref(route.params.projectId || null)
  const sessionId = ref(route.query.sessionId || null)
  const messages = ref([])
  const outline = ref('')
  const personaName = ref('')
  const ended = ref(false)

  // ---------- 流程状态 ----------
  const loading = ref(false) // 历史加载
  const starting = ref(false) // 开始面试
  const ending = ref(false) // 结束面试

  // ---------- 配置状态 ----------
  const personas = ref([])
  const selectedPersonaId = ref(null)
  const referenceWrittenTest = ref(false)

  // ---------- 派生数据 ----------
  const currentPersonaId = computed(() => {
    for (let i = messages.value.length - 1; i >= 0; i--) {
      const m = messages.value[i]
      if (m.role === 'INTERVIEWER' && m.personaId) return m.personaId
    }
    return null
  })

  const currentPersonaName = computed(() => {
    const p = personas.value.find((x) => x.id === currentPersonaId.value)
    return p?.name || personaName.value || '面试官'
  })

  const currentPersonaStyle = computed(() => {
    const p = personas.value.find((x) => x.id === currentPersonaId.value)
    return p?.styleConfig || null
  })

  // ---------- 初始化 ----------
  onMounted(async () => {
    // 完整流程（笔试→面试）：自动勾选「引用笔试结果」
    if (route.query.refWritten === '1' || route.query.refWritten === 'true') {
      referenceWrittenTest.value = true
    }
    await loadPersonas()
    if (sessionId.value) {
      await loadHistory()
    }
  })

  async function loadPersonas() {
    try {
      const res = await getPersonaList()
      const list = res?.data || []
      personas.value = Array.isArray(list) ? list : []
      if (personas.value.length && !selectedPersonaId.value) {
        const preset = personas.value.find((p) => p.isPreset) || personas.value[0]
        selectedPersonaId.value = preset.id
      }
    } catch {
      // 拦截器已提示
    }
  }

  // 刷新后恢复历史消息
  async function loadHistory() {
    if (!sessionId.value) return
    loading.value = true
    try {
      const res = await getMockInterviewMessages(sessionId.value)
      const list = res?.data || []
      messages.value = Array.isArray(list) ? list : []
      // 当前面试官：取最近一条带 personaId 的面试官消息
      for (let i = messages.value.length - 1; i >= 0; i--) {
        const m = messages.value[i]
        if (m.role === 'INTERVIEWER' && m.personaId) {
          const p = personas.value.find((x) => x.id === m.personaId)
          if (p) personaName.value = p.name
          break
        }
      }
      ended.value = messages.value.some(
        (m) => m.role === 'INTERVIEWER' && m.content?.startsWith('【面试结束】')
      )
    } catch {
      // 拦截器已提示
    } finally {
      loading.value = false
    }
  }

  // ---------- 开始面试 ----------
  async function handleStart() {
    if (!projectId.value) {
      ElMessage.error('缺少项目信息，无法开始面试')
      return
    }
    if (starting.value) return
    starting.value = true
    try {
      const res = await startMockInterview(projectId.value, {
        personaId: selectedPersonaId.value || null,
        referenceWrittenTest: referenceWrittenTest.value,
      })
      const data = res?.data
      if (!data?.sessionId) {
        ElMessage.error(res?.message || '开始面试失败')
        return
      }
      sessionId.value = data.sessionId
      personaName.value = data.personaName
      outline.value = data.outline || ''
      messages.value = [
        {
          id: null,
          role: 'INTERVIEWER',
          content: data.openingMessage,
          personaId: data.personaId,
          createdAt: new Date(),
        },
      ]
      // 把 sessionId 写入 URL，刷新后可恢复历史
      router.replace({ path: `/mock-interview/${projectId.value}`, query: { sessionId: sessionId.value } })
    } catch {
      // 拦截器已提示
    } finally {
      starting.value = false
    }
  }

  // 跳转面试分析报告页
  function goAnalysis() {
    router.push({ path: `/analysis/session/${sessionId.value}`, query: { projectId: projectId.value } })
  }

  // ---------- 结束判定（面试官宣布结束） ----------
  async function handleInterviewEnded() {
    try {
      await ElMessageBox.confirm('面试官已宣布面试结束，是否现在查看面试分析？', '面试结束', {
        confirmButtonText: '查看分析',
        cancelButtonText: '留在本页',
        type: 'success',
      })
      goAnalysis()
    } catch {
      // 用户选择留在本页
    }
  }

  // ---------- 手动结束 ----------
  async function endInterview() {
    if (!sessionId.value || ended.value || ending.value) return
    try {
      await ElMessageBox.confirm('确定结束本次面试吗？系统将生成面试总结。', '结束面试', {
        confirmButtonText: '结束面试',
        cancelButtonText: '再想想',
        type: 'warning',
      })
    } catch {
      return
    }
    ending.value = true
    try {
      const res = await concludeMockInterview(sessionId.value)
      const summary = res?.data
      if (summary?.content) messages.value.push(summary)
      ended.value = true
      ElMessage.success('面试已结束，总结已生成')
      try {
        await ElMessageBox.alert('面试总结已生成，点击查看面试分析。', '面试完成', {
          confirmButtonText: '查看分析',
        })
        goAnalysis()
      } catch {
        // 用户关闭弹窗，留在本页
      }
    } catch {
      // 拦截器已提示
    } finally {
      ending.value = false
    }
  }

  return {
    // 状态
    projectId,
    sessionId,
    messages,
    outline,
    personaName,
    ended,
    loading,
    starting,
    ending,
    personas,
    selectedPersonaId,
    referenceWrittenTest,
    // 派生
    currentPersonaId,
    currentPersonaName,
    currentPersonaStyle,
    // 动作
    handleStart,
    endInterview,
    handleInterviewEnded,
    loadHistory,
    loadPersonas,
  }
}
