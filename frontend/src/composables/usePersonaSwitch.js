/**
 * 换面试官（人设切换）编排：
 * - 手动切换：弹窗选择 + switchPersona + 插入系统消息
 * - 面试官自动建议换人：AI 依据回答情况推荐，候选人确认后切换
 * 状态（弹窗可见性 / 待切换 id / 请求中）与接口调用都在这里，父组件只负责调用。
 */
import { ref } from 'vue'
import { switchPersona } from '../api/mock-interview'

/**
 * @param {object} options
 * @param {import('vue').Ref<string|null>} options.sessionId 当前面试会话 id
 * @param {import('vue').Ref<Array>} options.personas 面试官风格列表
 * @param {import('vue').Ref<string>} options.personaName 当前面试官名称（可写）
 * @param {import('vue').Ref<Array>} options.messages 消息列表（用于插入系统提示）
 * @param {import('vue').ComputedRef<any>} options.currentPersonaId 当前生效的人设 id
 * @param {() => boolean} [options.isEnded] 面试是否已结束
 */
export function usePersonaSwitch(options = {}) {
  const {
    sessionId = null,
    personas = null,
    personaName = null,
    messages = null,
    currentPersonaId = null,
    isEnded = () => false,
  } = options

  const switching = ref(false) // 换面试官请求中
  const switchDialogVisible = ref(false) // 换面试官弹窗
  const switchPersonaId = ref(null) // 弹窗中待切换的人设 id

  function currentId() {
    return currentPersonaId ? currentPersonaId.value : null
  }

  // 插入一条“面试官已切换”的系统提示
  function pushSwitchedNotice(name) {
    messages.value.push({
      id: null,
      role: 'SYSTEM',
      content: `面试官已切换为【${name}】`,
      createdAt: new Date(),
    })
  }

  function openSwitchDialog() {
    if (!sessionId?.value || switching.value) return
    switchPersonaId.value = currentId()
    switchDialogVisible.value = true
  }

  function setSwitchPersonaId(id) {
    switchPersonaId.value = id
  }

  async function confirmSwitch() {
    if (!switchPersonaId.value) {
      ElMessage.warning('请选择新的面试官风格')
      return
    }
    switching.value = true
    try {
      await switchPersona(sessionId.value, switchPersonaId.value)
      const p = personas.value.find((x) => x.id === switchPersonaId.value)
      if (p) personaName.value = p.name
      pushSwitchedNotice(personaName.value)
      ElMessage.success('面试官已切换')
      switchDialogVisible.value = false
    } catch {
      // 拦截器已提示
    } finally {
      switching.value = false
    }
  }

  // 面试官自动建议换人（AI 依据回答情况推荐，候选人确认后切换）
  async function handleSwitchSuggestion(suggestedPersonaId) {
    if (!suggestedPersonaId || switching.value || isEnded()) return
    const p = personas.value.find((x) => x.id === suggestedPersonaId)
    if (!p || p.id === currentId()) return
    try {
      await ElMessageBox.confirm(
        `面试官根据你的回答情况，建议切换为【${p.name}】风格继续面试，是否切换？`,
        '面试官建议换人',
        {
          confirmButtonText: '切换',
          cancelButtonText: '暂不',
          type: 'info',
        }
      )
    } catch {
      return // 用户暂不切换
    }
    switching.value = true
    try {
      await switchPersona(sessionId.value, p.id)
      personaName.value = p.name
      pushSwitchedNotice(p.name)
      ElMessage.success('已按建议切换面试官')
    } catch {
      // 拦截器已提示
    } finally {
      switching.value = false
    }
  }

  return {
    switching,
    switchDialogVisible,
    switchPersonaId,
    setSwitchPersonaId,
    openSwitchDialog,
    confirmSwitch,
    handleSwitchSuggestion,
  }
}
