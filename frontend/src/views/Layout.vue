<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'

const route = useRoute()

// 根据当前路由路径决定侧边栏激活项
const activeMenu = computed(() => {
  if (route.path.startsWith('/settings')) return '/settings'
  return '/'
})
</script>

<template>
  <el-container class="layout">
    <el-aside width="240px" class="aside">
      <div class="brand">
        <div class="brand-name">AI面试模拟</div>
        <div class="brand-sub">Java 面试训练</div>
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
          <span class="menu-icon">🏠</span>
          <span>首页</span>
        </el-menu-item>
        <el-menu-item index="/settings">
          <span class="menu-icon">⚙️</span>
          <span>设置</span>
        </el-menu-item>
      </el-menu>
    </el-aside>

    <el-main class="main">
      <router-view />
    </el-main>
  </el-container>
</template>

<style scoped>
.layout {
  min-height: 100vh;
}
.aside {
  background: #001529;
  display: flex;
  flex-direction: column;
}
.brand {
  padding: 24px 20px 16px;
  color: #fff;
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
.main {
  background: #f5f7fa;
  padding: 24px;
  overflow-y: auto;
}
</style>
