/**
 * 面试官风格 styleConfig 的取值字典与表单互转。
 *
 * **分工必须记住**（同一个 JSON，两个消费者）：
 * - `tone` / `followupStrategy` / `aggressiveness` → 进 LLM 系统提示词，决定「问什么」
 *   （映射见后端 `PersonaStyleText`）；
 * - `qwenVoice` / `qwenLanguage` / `qwenInstruct` → 进 TTS，决定「怎么念」
 *   （映射见后端 `LocalQwenVoiceService.resolvePersonaVoice`）。
 *
 * `speed` 和 `qwenInstruct` 会被组装进 TTS 的风格指令，但 **0.6B 权重会丢弃 instruct**
 * （faster_qwen3_tts 对 `tts_model_size == "0b6"` 直接 `instruct = None`），
 * 所以这两项只有换 1.7B 权重才真正生效，界面上必须标明，不能让用户以为调了有用。
 */

/** Qwen3-TTS CustomVoice 的 9 个预置音色（模型固定；换权重才会变） */
export const VOICE_OPTIONS = [
  'aiden',
  'dylan',
  'eric',
  'ono_anna',
  'ryan',
  'serena',
  'sohee',
  'uncle_fu',
  'vivian',
].map((value) => ({ value, label: value }))

/** 语气：既影响提示词里的措辞温度，也（1.7B 下）影响声音 */
export const TONE_OPTIONS = [
  { value: 'serious', label: '严肃冷静', detail: '冷静克制、措辞严谨，不寒暄也不夸奖', speaker: 'uncle_fu' },
  { value: 'stern', label: '严厉强势', detail: '直接、有压迫感，句子短促，不用缓冲语', speaker: 'dylan' },
  { value: 'warm', label: '温和鼓励', detail: '温和鼓励，像带新人的资深工程师，先肯定再追问', speaker: 'serena' },
  { value: 'professional', label: '专业稳重', detail: '稳重、注重细节，只就事论事', speaker: 'uncle_fu' },
  { value: 'neutral', label: '平稳中性', detail: '平稳自然', speaker: 'vivian' },
]

/** 追问策略：决定追问的**方向**（真正进提示词，一直生效） */
export const FOLLOWUP_OPTIONS = [
  { value: 'deep_dive', label: '深挖底层', detail: '不断追问底层原理和实现细节，刨根问底到源码级别' },
  { value: 'challenge', label: '质疑施压', detail: '质疑回答、施加压力，重点考察抗压能力' },
  { value: 'guide', label: '引导式', detail: '友好引导，答不上来时给提示并换角度提问' },
  { value: 'project_drill', label: '项目深挖', detail: '围绕简历项目深挖架构决策、踩坑经验与技术选型理由' },
  { value: 'checklist', label: '清单式', detail: '按知识点清单逐个考察，不追问过深' },
]

/** 语速：只有 1.7B 权重才生效 */
export const SPEED_OPTIONS = [
  { value: 'slow', label: '偏慢' },
  { value: 'medium', label: '中等' },
  { value: 'fast', label: '偏快' },
]

/** 语种：真正生效 */
export const LANGUAGE_OPTIONS = [
  { value: 'Chinese', label: '中文' },
  { value: 'English', label: '英文' },
]

/** 追问强度（0~1）：真正进提示词，一直生效 */
export function aggressivenessLabel(value) {
  const v = Number(value)
  if (!Number.isFinite(v)) return '未设置'
  if (v >= 0.8) return '高压'
  if (v >= 0.6) return '偏严'
  if (v >= 0.3) return '中性'
  return '温和'
}

export function emptyPersonaStyleForm() {
  return {
    tone: '',
    followupStrategy: '',
    aggressiveness: '',
    speed: '',
    qwenVoice: '',
    qwenInstruct: '',
    qwenLanguage: 'Chinese',
  }
}

/** styleConfig(JSON 字符串) → 表单对象。解析失败时 valid=false，但表单仍可用。 */
export function parseStyleConfig(json) {
  const form = emptyPersonaStyleForm()
  if (!json || !json.trim()) return { form, valid: true }
  let node
  try {
    node = JSON.parse(json)
  } catch (e) {
    return { form, valid: false }
  }
  if (!node || typeof node !== 'object' || Array.isArray(node)) {
    return { form, valid: false }
  }
  if (typeof node.tone === 'string') form.tone = node.tone
  if (typeof node.followupStrategy === 'string') form.followupStrategy = node.followupStrategy
  if (typeof node.aggressiveness === 'number') form.aggressiveness = node.aggressiveness
  if (typeof node.speed === 'string') form.speed = node.speed
  if (typeof node.qwenVoice === 'string') form.qwenVoice = node.qwenVoice
  if (typeof node.qwenInstruct === 'string') form.qwenInstruct = node.qwenInstruct
  if (typeof node.qwenLanguage === 'string' && node.qwenLanguage) form.qwenLanguage = node.qwenLanguage
  return { form, valid: true }
}

/** 表单对象 → styleConfig(JSON 字符串)。只写非空项，避免存一堆空字段。 */
export function buildStyleConfig(form) {
  const cfg = {}
  if (form.tone) cfg.tone = form.tone
  if (form.followupStrategy) cfg.followupStrategy = form.followupStrategy
  if (form.aggressiveness !== '' && form.aggressiveness !== null && form.aggressiveness !== undefined) {
    const v = Number(form.aggressiveness)
    if (Number.isFinite(v)) cfg.aggressiveness = Math.round(v * 100) / 100
  }
  if (form.speed) cfg.speed = form.speed
  if (form.qwenVoice) cfg.qwenVoice = form.qwenVoice
  if (form.qwenInstruct && form.qwenInstruct.trim()) cfg.qwenInstruct = form.qwenInstruct.trim()
  if (form.qwenLanguage && form.qwenLanguage !== 'Chinese') cfg.qwenLanguage = form.qwenLanguage
  return Object.keys(cfg).length ? JSON.stringify(cfg) : ''
}

/**
 * 一句话摘要，给列表用。
 * 同时把「哪些项在当前权重下不生效」标出来，这是用户最容易误解的地方。
 */
export function summarizeStyleConfig(json) {
  if (!json || !json.trim()) return '未配置（默认音色 + 动态深挖）'
  const { form, valid } = parseStyleConfig(json)
  if (!valid) return '配置不是合法 JSON'
  const parts = []
  const followup = FOLLOWUP_OPTIONS.find((o) => o.value === form.followupStrategy)
  parts.push(followup ? followup.label : form.followupStrategy ? '自定义追问' : '动态深挖')
  const tone = TONE_OPTIONS.find((o) => o.value === form.tone)
  if (tone) parts.push(tone.label)
  if (form.aggressiveness !== '') parts.push(`强度 ${form.aggressiveness}（${aggressivenessLabel(form.aggressiveness)}）`)
  parts.push(form.qwenVoice || (tone?.speaker ? `${tone.speaker}（随语气）` : 'uncle_fu'))
  if (form.qwenLanguage && form.qwenLanguage !== 'Chinese') parts.push(form.qwenLanguage)
  return parts.join(' · ')
}

/** 当前权重下不生效的项（0.6B 会丢弃 instruct），给表单做提示用 */
export function ineffectiveKeys(form) {
  const keys = []
  if (form.speed) keys.push('语速')
  if (form.qwenInstruct && form.qwenInstruct.trim()) keys.push('自定义语气指令')
  return keys
}
