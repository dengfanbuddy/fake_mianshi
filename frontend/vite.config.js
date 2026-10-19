import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import AutoImport from 'unplugin-auto-import/vite'
import Components from 'unplugin-vue-components/vite'
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers'

// https://vite.dev/config/
export default defineConfig({
  plugins: [
    vue(),
    /*
     * Element Plus 按需引入。
     *
     * 原先 main.js 里 `app.use(ElementPlus)` + 全量 `element-plus/dist/index.css`：
     * 会把 40+ 个组件、全部指令和整份主题样式一次性打进主 chunk（769 kB，gzip 243 kB）。
     * 现在由插件按模板里实际出现的 <el-xxx> / v-loading / ElMessage 自动补 import 与样式，
     * 未使用的组件不再进入产物。
     */
    AutoImport({
      resolvers: [ElementPlusResolver({ importStyle: 'css' })],
      // 不生成 d.ts：纯 JS 项目，避免多出无用的类型声明文件
      dts: false,
    }),
    Components({
      resolvers: [ElementPlusResolver({ importStyle: 'css' })],
      dts: false,
    }),
  ],
  server: {
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        ws: true, // 转发 WebSocket 升级（流式语音 /api/ws/*）
      },
    },
  },
})
