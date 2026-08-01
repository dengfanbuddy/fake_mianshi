import { defineStore } from 'pinia'
import { ref } from 'vue'

// 考试会话持久化到 sessionStorage，刷新页面后仍可恢复
const STORAGE_KEY = 'written-test:session'

export const useWrittenTestStore = defineStore('writtenTest', () => {
  const session = ref(null)

  function load() {
    try {
      const raw = sessionStorage.getItem(STORAGE_KEY)
      if (raw) session.value = JSON.parse(raw)
    } catch (e) {
      session.value = null
    }
    return session.value
  }

  function save(data) {
    session.value = data
    sessionStorage.setItem(STORAGE_KEY, JSON.stringify(data))
  }

  function clear() {
    session.value = null
    sessionStorage.removeItem(STORAGE_KEY)
  }

  return { session, load, save, clear }
})
