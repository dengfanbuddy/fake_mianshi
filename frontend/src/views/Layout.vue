<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'

const route = useRoute()

// 根据当前路由路径决定侧边栏激活项：业务页（项目/笔试/面试/分析）统一归属"项目管理"
const activeMenu = computed(() => {
  const p = route.path
  if (p.startsWith('/settings')) return '/settings'
  if (p.startsWith('/help') || p.startsWith('/docs')) return '/help'
  if (
    p.startsWith('/projects') ||
    p.startsWith('/project') ||
    p.startsWith('/analysis') ||
    p.startsWith('/written-test') ||
    p.startsWith('/mock-interview')
  ) {
    return '/projects'
  }
  return '/'
})
</script>

<template>
  <el-container class="layout">
    <el-aside width="240px" class="aside">
      <div class="brand">
        <div class="brand-name">AI面试模拟</div>
        <div class="brand-sub">AI 面试训练</div>
      </div>
      <el-menu
        :default-active="activeMenu"
        router
        class="menu"
        background-color="#001529"
        text-color="#a6adb4"
        active-text-color="#ffffff"
      >
        <el-menu-item index="/">
          <span class="menu-icon">📊</span>
          <span>看板</span>
        </el-menu-item>
        <el-menu-item index="/projects">
          <span class="menu-icon">📁</span>
          <span>项目管理</span>
        </el-menu-item>
        <el-menu-item index="/settings">
          <span class="menu-icon">⚙️</span>
          <span>设置</span>
        </el-menu-item>
        <el-menu-item index="/prompt-templates">
          <span class="menu-icon">📝</span>
          <span>提示词管理</span>
        </el-menu-item>
        <el-menu-item index="/help">
          <span class="menu-icon">❓</span>
          <span>使用说明</span>
        </el-menu-item>
      </el-menu>
      <div class="aside-footer">v1.0.0</div>
    </el-aside>

    <el-main class="main">
      <router-view />
    </el-main>
  </el-container>
</template>

<style scoped>
.layout {
  height: 100vh;
  overflow: hidden;
}
.aside {
  background: #001529;
  display: flex;
  flex-direction: column;
  overflow-y: auto;
}
.brand {
  padding: 24px 20px 16px;
  color: #fff;
  flex-shrink: 0;
}
.brand-name {
  font-size: 22px;
  font-weight: 700;
  letter-spacing: 1px;
}
.brand-sub {
  margin-top: 4px;
  font-size: 12px;
  color: #a6adb4;
}
.menu {
  border-right: none;
  flex: 1;
}
.menu-icon {
  margin-right: 6px;
}
.aside-footer {
  flex-shrink: 0;
  padding: 16px 20px;
  font-size: 12px;
  color: #4a5568;
}
.main {
  background: #f5f7fa;
  padding: 24px;
  overflow-y: auto;
  height: 100vh;
}
</style>
