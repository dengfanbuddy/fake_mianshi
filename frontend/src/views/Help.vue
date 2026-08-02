<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'

const router = useRouter()
const activeTab = ref('guide')

const guideSteps = [
  { title: '1. 新建项目', desc: '在「项目管理」页新建面试项目，填写目标岗位（如后端开发 / 产品经理），系统按职业匹配提示词。', action: '去项目管理 →', path: '/projects' },
  { title: '2. 上传简历', desc: '在项目详情页上传 PDF 简历，AI 自动解析并分析技术栈、经验年限、建议岗位。', action: null, path: '' },
  { title: '3. 确认职位需求', desc: '从简历生成或手动填写职位需求（岗位、资历、技术栈、JD），用于精准出题。', action: null, path: '' },
  { title: '4. 笔试', desc: '单选/多选/填空/简答，限时 60 分钟，自动判分，简答由 AI 评分。', action: null, path: '' },
  { title: '5. 模拟面试', desc: '选择面试官风格（技术深挖/压力/温和/项目实战/八股文），按住说话语音作答，面试官动态追问，可中途换人。', action: null, path: '' },
  { title: '6. 查看分析报告', desc: '综合评分、知识盲区（含答案）、逐题打分依据、改进方案（含实例）、能力等级与薪资预估。', action: null, path: '' },
  { title: '7. 再次面试', desc: '系统基于历史弱点标签自适应出题，优先考察薄弱知识点，反复训练直至补齐。', action: null, path: '' },
]

const aiSteps = [
  { title: '1. 打开设置页', desc: '进入「设置」→ AI 模型配置。', },
  { title: '2. 编辑/新增配置', desc: 'Provider 填 deepseek，API 地址填 OpenAI 兼容端点：https://api.deepseek.com/v1/chat/completions，填入你的 API Key，模型名如 deepseek-chat，勾选"设为启用"后保存。', },
  { title: '3. 切换其他服务商', desc: '系统兼容所有 OpenAI 格式接口的服务商（如通义千问、Kimi、GLM 等）：把 API 地址换成对应服务的 OpenAI 兼容端点并填 Key 即可。', },
  { title: '4. 注意事项', desc: '必须有一个"已启用"的配置，出题/面试/分析才可用；API Key 仅存储在本机数据库。', },
]

const voiceSteps = [
  { title: '1. 开通服务', desc: '登录腾讯云控制台，开通「语音识别」（一句话识别）与「语音合成」服务（有免费额度）。', link: '语音识别控制台 → https://console.cloud.tencent.com/asr', },
  { title: '2. 配置密钥', desc: '在后端环境变量中配置（重启后端生效）：TENCENT_SECRET_ID、TENCENT_SECRET_KEY、TENCENT_APP_ID。', },
  { title: '3. 验证', desc: '设置页会显示"已配置"状态；模拟面试中录音说话即用 STT，面试官回复自动 TTS 朗读。', },
  { title: '4. 更换其他语音服务商', desc: '系统语音服务已接口化（VoiceService），当前实现为腾讯云。如需接入阿里云/讯飞等，在后端新增对应实现类（如 AliyunVoiceServiceImpl）并替换注入即可，前端无需改动。', },
]

const faqs = [
  { q: 'AI 出题/分析报错怎么办？', a: '确认设置页 AI 模型配置已启用且 API Key 正确；报"格式异常"时重启后端后重试"重新生成分析"。' },
  { q: '语音识别/合成不可用？', a: '确认腾讯云两个服务都已开通、后端环境变量已配置且重启过；设置页查看语音配置状态。' },
  { q: '换面试官后声音没变？', a: '不同风格映射不同音色（严肃/压力→男声、温和→女声）；若自定义风格未设置 tone 字段则用默认音色。' },
  { q: '数据存在哪里？', a: '全部存在本地 backend/data/fake_mianshi.db（SQLite），简历 PDF 和录音在 backend/uploads/。' },
]
</script>

<template>
  <div class="help">
    <el-tabs v-model="activeTab" class="tabs">
      <!-- 使用流程 -->
      <el-tab-pane label="使用流程" name="guide">
        <div class="steps">
          <div v-for="(s, i) in guideSteps" :key="i" class="step-card">
            <div class="step-no">{{ i + 1 }}</div>
            <div class="step-body">
              <div class="step-title">{{ s.title }}</div>
              <div class="step-desc">{{ s.desc }}</div>
              <el-button v-if="s.action" link type="primary" size="small" @click="router.push(s.path)">
                {{ s.action }}
              </el-button>
            </div>
          </div>
        </div>
      </el-tab-pane>

      <!-- AI 模型配置 -->
      <el-tab-pane label="AI 模型配置" name="ai">
        <div class="steps">
          <div v-for="(s, i) in aiSteps" :key="i" class="step-card">
            <div class="step-no">{{ i + 1 }}</div>
            <div class="step-body">
              <div class="step-title">{{ s.title }}</div>
              <div class="step-desc">{{ s.desc }}</div>
            </div>
          </div>
        </div>
      </el-tab-pane>

      <!-- 云语音配置 -->
      <el-tab-pane label="云语音配置" name="voice">
        <div class="steps">
          <div v-for="(s, i) in voiceSteps" :key="i" class="step-card">
            <div class="step-no">{{ i + 1 }}</div>
            <div class="step-body">
              <div class="step-title">{{ s.title }}</div>
              <div class="step-desc">{{ s.desc }}</div>
              <a v-if="s.link" class="step-link" :href="s.link.split(' → ')[1]" target="_blank">
                {{ s.link.split(' → ')[0] }} ↗
              </a>
            </div>
          </div>
        </div>
      </el-tab-pane>

      <!-- 常见问题 -->
      <el-tab-pane label="常见问题" name="faq">
        <div class="faq-list">
          <div v-for="(f, i) in faqs" :key="i" class="faq-item">
            <div class="faq-q">Q：{{ f.q }}</div>
            <div class="faq-a">A：{{ f.a }}</div>
          </div>
        </div>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<style scoped>
.help {
  max-width: 900px;
  margin: 0 auto;
}
.tabs :deep(.el-tabs__header) {
  margin-bottom: 20px;
}
.steps {
  display: flex;
  flex-direction: column;
  gap: 14px;
}
.step-card {
  display: flex;
  gap: 14px;
  background: #fff;
  border: 1px solid #ebeef5;
  border-radius: 12px;
  padding: 16px 20px;
}
.step-no {
  flex-shrink: 0;
  width: 30px;
  height: 30px;
  border-radius: 50%;
  background: #409eff;
  color: #fff;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 14px;
}
.step-title {
  font-size: 15px;
  font-weight: 700;
  color: #303133;
  margin-bottom: 6px;
}
.step-desc {
  font-size: 13px;
  line-height: 1.8;
  color: #606266;
  white-space: pre-wrap;
}
.step-link {
  display: inline-block;
  margin-top: 8px;
  font-size: 13px;
  color: #409eff;
  text-decoration: none;
}
.step-link:hover {
  text-decoration: underline;
}
.faq-list {
  display: flex;
  flex-direction: column;
  gap: 14px;
}
.faq-item {
  background: #fff;
  border: 1px solid #ebeef5;
  border-radius: 12px;
  padding: 16px 20px;
}
.faq-q {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 6px;
}
.faq-a {
  font-size: 13px;
  line-height: 1.8;
  color: #606266;
}
</style>
