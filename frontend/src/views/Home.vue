<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { getDashboardStats } from '../api/session'
import { getProjects } from '../api/project'

const router = useRouter()
const loading = ref(true)
const stats = ref({ projectCount: 0, sessionCount: 0, writtenCount: 0, mockCount: 0 })
const recentProjects = ref([])

const techStack = [
  { name: 'Spring Boot 4.1', role: '后端框架', desc: 'Java 17 · REST API · JPA' },
  { name: 'SQLite', role: '数据库', desc: '零配置本地存储 · Hibernate 方言' },
  { name: 'Vue 3 + Vite', role: '前端框架', desc: 'Composition API · 组件化' },
  { name: 'Element Plus', role: 'UI 组件库', desc: '现代化交互组件' },
  { name: 'DeepSeek API', role: 'AI 引擎', desc: '出题 · 面试对话 · 分析报告' },
  { name: '腾讯云语音', role: '语音服务', desc: 'ASR 识别 · TTS 合成' },
]

const architecture = [
  { layer: '前端', items: ['Vue 3 单页应用', '笔试 / 模拟面试 / 分析报告 / 设置', '录音 · 播放 · 交互'], color: '#409eff' },
  { layer: '后端', items: ['Spring Boot REST API', '出题引擎 · 面试对话 · 分析服务 · 语音接入', '全局异常 · 统一响应'], color: '#67c23a' },
  { layer: 'AI 与语音', items: ['DeepSeek 大模型（可配置多服务商）', '腾讯云 ASR / TTS（接口化，可扩展阿里等）', 'Prompt 工程：出题 / 追问 / 评分依据'], color: '#e6a23c' },
  { layer: '存储', items: ['SQLite 本地数据库', '简历 PDF / 录音文件', '弱点标签 · 历史分析'], color: '#f56c6c' },
]

async function fetchAll() {
  loading.value = true
  try {
    const sres = await getDashboardStats()
    if (sres.code === 200) stats.value = sres.data || stats.value
    const pres = await getProjects()
    if (pres.code === 200) {
      recentProjects.value = (pres.data || []).slice(0, 4)
    }
  } catch (e) {
    // 拦截器已提示
  } finally {
    loading.value = false
  }
}

onMounted(fetchAll)
</script>

<template>
  <div class="dashboard" v-loading="loading">
    <!-- 欢迎与快捷入口 -->
    <section class="hero">
      <div class="hero-left">
        <h1 class="hero-title">AI 面试模拟系统</h1>
        <p class="hero-sub">面向 Java 开发者的求职面试训练：上传简历 → AI 出题 → 笔试 + 语音模拟面试 → 多维分析 → 自适应查漏补缺</p>
        <div class="hero-actions">
          <el-button type="primary" @click="router.push('/projects')">管理项目</el-button>
          <el-button @click="router.push('/help')">查看使用说明</el-button>
        </div>
      </div>
      <div class="hero-stats">
        <div class="stat-box">
          <div class="stat-num">{{ stats.projectCount }}</div>
          <div class="stat-label">面试项目</div>
        </div>
        <div class="stat-box">
          <div class="stat-num">{{ stats.writtenCount }}</div>
          <div class="stat-label">笔试场次</div>
        </div>
        <div class="stat-box">
          <div class="stat-num">{{ stats.mockCount }}</div>
          <div class="stat-label">模拟面试</div>
        </div>
      </div>
    </section>

    <!-- 最近项目 -->
    <section v-if="recentProjects.length" class="section">
      <div class="section-header">
        <h3 class="section-title">最近项目</h3>
        <el-button link type="primary" @click="router.push('/projects')">查看全部 →</el-button>
      </div>
      <div class="recent-list">
        <div
          v-for="p in recentProjects"
          :key="p.id"
          class="recent-item"
          @click="router.push(`/project/${p.id}`)"
        >
          <div class="recent-name">{{ p.name }}</div>
          <div class="recent-desc">{{ p.description || '暂无描述' }}</div>
        </div>
      </div>
    </section>

    <!-- 技术栈 -->
    <section class="section">
      <h3 class="section-title">技术栈</h3>
      <div class="tech-grid">
        <div v-for="t in techStack" :key="t.name" class="tech-card">
          <div class="tech-name">{{ t.name }}</div>
          <div class="tech-role">{{ t.role }}</div>
          <div class="tech-desc">{{ t.desc }}</div>
        </div>
      </div>
    </section>

    <!-- 架构 -->
    <section class="section">
      <h3 class="section-title">系统架构</h3>
      <div class="arch-grid">
        <div v-for="a in architecture" :key="a.layer" class="arch-card">
          <div class="arch-layer" :style="{ background: a.color }">{{ a.layer }}</div>
          <ul class="arch-items">
            <li v-for="(item, i) in a.items" :key="i">{{ item }}</li>
          </ul>
        </div>
      </div>
      <div class="arch-flow">
        <el-tag type="info" effect="plain" size="large">简历 PDF</el-tag>
        <span class="flow-arrow">→</span>
        <el-tag type="success" effect="plain" size="large">AI 分析</el-tag>
        <span class="flow-arrow">→</span>
        <el-tag type="primary" effect="plain" size="large">笔试 / 模拟面试</el-tag>
        <span class="flow-arrow">→</span>
        <el-tag type="warning" effect="plain" size="large">分析报告</el-tag>
        <span class="flow-arrow">→</span>
        <el-tag type="danger" effect="plain" size="large">弱点标签 · 自适应出题</el-tag>
      </div>
    </section>

    <!-- 关于 -->
    <section class="section">
      <h3 class="section-title">关于</h3>
      <div class="about-card">
        <div class="about-row"><span class="about-label">版本</span><span>v1.0.0</span></div>
        <div class="about-row"><span class="about-label">定位</span><span>个人求职面试训练工具，Java 后端方向</span></div>
        <div class="about-row"><span class="about-label">隐私</span><span>数据全部存储在本地 SQLite，AI 调用仅发送任务所需上下文</span></div>
      </div>
    </section>
  </div>
</template>

<style scoped>
.dashboard {
  max-width: 1060px;
  margin: 0 auto;
  display: flex;
  flex-direction: column;
  gap: 22px;
}
.hero {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
  background: linear-gradient(120deg, #1f3a5f 0%, #2c5b8f 100%);
  border-radius: 16px;
  padding: 32px 36px;
  color: #fff;
}
.hero-left {
  flex: 1;
}
.hero-title {
  margin: 0 0 10px;
  font-size: 26px;
  font-weight: 800;
  letter-spacing: 1px;
}
.hero-sub {
  margin: 0 0 20px;
  font-size: 14px;
  line-height: 1.8;
  color: rgba(255, 255, 255, 0.82);
  max-width: 560px;
}
.hero-actions {
  display: flex;
  gap: 12px;
}
.hero-stats {
  display: flex;
  gap: 20px;
  flex-shrink: 0;
}
.stat-box {
  text-align: center;
  background: rgba(255, 255, 255, 0.12);
  border-radius: 12px;
  padding: 16px 22px;
  min-width: 92px;
}
.stat-num {
  font-size: 28px;
  font-weight: 800;
}
.stat-label {
  font-size: 12px;
  color: rgba(255, 255, 255, 0.75);
  margin-top: 4px;
}
.section {
  background: #fff;
  border-radius: 14px;
  padding: 22px 26px;
  border: 1px solid #ebeef5;
}
.section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 14px;
}
.section-title {
  margin: 0 0 14px;
  font-size: 16px;
  font-weight: 700;
  color: #303133;
}
.section-header .section-title {
  margin: 0;
}
.recent-list {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  gap: 12px;
}
.recent-item {
  padding: 12px 14px;
  border: 1px solid #ebeef5;
  border-radius: 10px;
  cursor: pointer;
  transition: all 0.2s;
}
.recent-item:hover {
  border-color: #409eff;
  box-shadow: 0 2px 10px rgba(64, 158, 255, 0.15);
}
.recent-name {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 4px;
}
.recent-desc {
  font-size: 12px;
  color: #909399;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.tech-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
  gap: 14px;
}
.tech-card {
  border: 1px solid #ebeef5;
  border-radius: 10px;
  padding: 14px 16px;
  background: #fafbfc;
}
.tech-name {
  font-size: 15px;
  font-weight: 700;
  color: #303133;
}
.tech-role {
  display: inline-block;
  font-size: 11px;
  color: #409eff;
  background: #ecf5ff;
  border-radius: 4px;
  padding: 1px 8px;
  margin: 6px 0;
}
.tech-desc {
  font-size: 12px;
  color: #909399;
}
.arch-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
  gap: 14px;
  margin-bottom: 18px;
}
.arch-card {
  border: 1px solid #ebeef5;
  border-radius: 10px;
  overflow: hidden;
}
.arch-layer {
  color: #fff;
  font-size: 14px;
  font-weight: 700;
  padding: 8px 14px;
}
.arch-items {
  list-style: none;
  margin: 0;
  padding: 12px 14px;
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.arch-items li {
  font-size: 12px;
  color: #606266;
  line-height: 1.6;
  padding-left: 12px;
  position: relative;
}
.arch-items li::before {
  content: '';
  position: absolute;
  left: 0;
  top: 8px;
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #c0c4cc;
}
.arch-flow {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  background: #f7f8fa;
  border-radius: 10px;
  padding: 14px 16px;
}
.flow-arrow {
  color: #909399;
  font-weight: 700;
}
.about-card {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.about-row {
  display: flex;
  gap: 12px;
  font-size: 13px;
  color: #606266;
}
.about-label {
  width: 52px;
  flex-shrink: 0;
  color: #909399;
}
</style>
