import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import router from './router'
import './style.css'

/*
 * Element Plus 不再全量注册：组件、指令（v-loading）与 ElMessage / ElMessageBox
 * 由 vite.config.js 里的按需引入插件自动 import（含各自样式），
 * 未用到的组件不会打进产物。
 */
const app = createApp(App)

app.use(createPinia())
app.use(router)

app.mount('#app')
