/**
 * 笔试题型相关的纯函数与常量（考试页与结果页共用）。
 *
 * 抽出来的原因：这些映射原来散在 586 行的 WrittenTestExam.vue 里，
 * 题型标签/标签色/选项解析都是纯函数，拆组件时必须只有一个真相。
 */
import { marked } from 'marked'
import DOMPurify from 'dompurify'

/** 题目内容支持 markdown（代码块、列表），经 DOMPurify 消毒防 XSS */
export function renderMd(text) {
  if (!text) return ''
  return DOMPurify.sanitize(marked.parse(text))
}

/** 0 → 'A'，1 → 'B' … */
export function letter(index) {
  return String.fromCharCode(65 + index)
}

/**
 * 选项文本：去掉后端自带的字母前缀，避免渲染成「A. A. 选项内容」。
 *
 * 出题链路存进 DB 的 options 本身就带 "A. "/"B、"/"C)" 之类前缀（见 written_test_question），
 * 而界面又会按序号补一个字母，所以必须在这里剥一次。只剥和序号对得上的那个字母，
 * 对不上（或自定义标识）就原样保留。
 */
export function optionText(option, index) {
  const raw = String(option ?? '')
  const matched = raw.match(/^\s*([A-Za-z])\s*[.、)．:：]\s*/)
  if (matched && matched[1].toUpperCase() === letter(index)) {
    return raw.slice(matched[0].length)
  }
  return raw
}

/** options 是 JSON 字符串（可能是脏数据），解析失败按空数组处理 */
export function parseOptions(question) {
  try {
    const arr = JSON.parse(question?.options || '[]')
    return Array.isArray(arr) ? arr : []
  } catch (e) {
    return []
  }
}

export const TYPE_LABEL = {
  SINGLE_CHOICE: '单选题',
  MULTIPLE_CHOICE: '多选题',
  FILL_BLANK: '填空题',
  SHORT_ANSWER: '简答题',
}

export const TYPE_TAG = {
  SINGLE_CHOICE: 'primary',
  MULTIPLE_CHOICE: 'warning',
  FILL_BLANK: 'success',
  SHORT_ANSWER: 'info',
}

/** 是否已作答（多选题看非空数组，其余看非空字符串） */
export function isAnswered(question, answers) {
  const val = answers[question.id]
  if (question.type === 'MULTIPLE_CHOICE') {
    return Array.isArray(val) && val.length > 0
  }
  return val !== undefined && val !== null && String(val).trim() !== ''
}

/**
 * 收集交卷答案。
 * 多选题按字母排序拼成 "AB"（后端按字符串比对），其余去空白。
 */
export function buildAnswers(questions, answers) {
  const result = {}
  for (const q of questions) {
    const val = answers[q.id]
    if (q.type === 'MULTIPLE_CHOICE') {
      if (Array.isArray(val) && val.length > 0) {
        result[q.id] = [...val].sort().join('')
      }
    } else if (val !== undefined && val !== null && String(val).trim() !== '') {
      result[q.id] = String(val).trim()
    }
  }
  return result
}
