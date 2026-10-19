# ADR-0008: 前端按需引入 Element Plus + 巨型组件拆分

**Date:** 2026-10-19
**Status:** Accepted

## 背景

前端有两个长期问题：

1. **Element Plus 全量注册**。`main.js` 里 `app.use(ElementPlus)` + `import 'element-plus/dist/index.css'`，
   把 40+ 个组件、全部指令和整份主题样式一次性打进主 chunk：
   实测 **`index.js` = 769 kB / gzip 243 kB**，且构建时一直报 "chunks larger than 500 kB"。
   项目实际只用到 43 个 `<el-xxx>` 标签，其余组件（表格、虚拟表格、树、日期选择器、级联、走马灯……）
   全被白白下载与解析。

2. **巨型 SFC**。`SessionReport.vue` 1638 行、`MockInterviewChat.vue` 1350 行、
   `ProjectDetail.vue` 1042 行，三个文件占了前端代码的近一半。模板、状态机、SSE/WebSocket/音频
   生命周期、样式全挤在一个文件里，任何改动都要在千行文件中定位，也很难复用。

## 决策

### 1. 按需引入（`unplugin-auto-import` + `unplugin-vue-components`）

- `vite.config.js` 注册 `AutoImport` 与 `Components`，两者都用 `ElementPlusResolver({ importStyle: 'css' })`；
- `main.js` 删掉 `app.use(ElementPlus)` 与全量 CSS；
- 全项目 **不再有任何 `from 'element-plus'` 显式 import**：`<el-xxx>` 组件、`v-loading` 指令、
  `ElMessage` / `ElMessageBox` 都由插件在构建期注入，并各自带上对应的 `theme-chalk` 样式。
- `dts: false`：纯 JS 项目，不生成无用的 `auto-imports.d.ts` / `components.d.ts`。

**效果**：入口 chunk 769 kB → **58 kB（gzip 243 kB → 21.5 kB，−92%）**，
Element Plus 的代码与样式改为按路由分块，500 kB 警告消失。

### 2. 巨型组件拆分

按「**视图只负责渲染、状态机进 composable、样式跟着组件走**」拆：

| 原文件 | 拆分后 |
|---|---|
| `SessionReport.vue` 1638 行 | `composables/useSessionAnalysis.js`（拉取 / SSE 流式 / 轮询兜底 / 重新生成）+ `components/analysis/*`（分数摘要、逐题评分、知识盲区、改进计划、性格沟通、生成中面板） |
| `MockInterviewChat.vue` 1350 行 | `composables/useVoiceStream.js`（WebSocket + PCM 录音/播放 + 卸载清理）+ `components/mock-interview/*`（消息气泡、输入栏、人设切换、控制条） |
| `ProjectDetail.vue` 1042 行 | `components/project/*`（历史表格、弱点标签、职位需求、简历、项目头部） |

拆分原则（写给后来者）：

- **搬移而不是重写**：模板结构与样式原样搬，只改 `props` / `emits` 数据流；
- **状态机放 composable**：凡是有定时器、EventSource、WebSocket、AudioContext 的，必须集中在一处导出
  `dispose()`，由父组件 `onBeforeUnmount` 调用——之前正是这类资源散落导致过泄漏；
- **子组件只做展示**：不直接调 API、不持有路由状态，方便以后写单测或改设计；
- **中文注释与文案不改**：拆分不夹带文案/行为变更，便于 review。

## 实测结果

| 文件 | 拆分前 | 拆分后 | 新建文件 |
|---|---|---|---|
| `SessionReport.vue` | 1638 | **298** | `useSessionAnalysis.js`(235) + `components/analysis/*` 13 个（28–236 行） |
| `MockInterviewChat.vue` | 1350 | **247** | `useVoiceStream.js`(289)、`useInterviewSession.js`(225)、`useInterviewReply.js`(266)、`usePersonaSwitch.js`(117) + `components/mock-interview/*` 4 个（60–297 行） |
| `ProjectDetail.vue` | 1042 | **296** | `components/project/*` 8 个（28–256 行） |

前端源码 30 个文件、9912 行，**除 3 个未在本次范围内的 view（`WrittenTestExam.vue` 586、`Settings.vue` 586、
`HistoryReport.vue` 479，主要是大段 `<style>`）外，没有超过 300 行的文件**。

构建产物：

| 指标 | 之前 | 之后 |
|---|---|---|
| 入口 chunk | 769 kB / gzip 243 kB | **58 kB / gzip 21.5 kB** |
| `SessionReport` chunk | —（全在入口里） | 26.7 kB（按需加载） |
| `ProjectDetail` chunk | — | 36.6 kB |
| `MockInterviewChat` chunk | — | 34.5 kB |
| 未使用的组件（`el-date-picker`/`el-tree`/`el-cascader`/`el-carousel`…） | 打进产物 | **0 处命中** |
| 构建告警 | "chunk > 500 kB" | 无 |

验证方式：`vite build` + 浏览器实测（`/`、`/projects`、`/settings`、`/project/1`、
`/analysis/session/3`、`/mock-interview/1`）——DOM 里**无未解析的 `el-*` 自定义元素**，
`--el-color-primary`、`.el-input__wrapper` 内阴影、`.el-table` 布局类等均为 Element Plus 的真实样式。


## 后果

**收益**

- 首屏只加载当前路由真正用到的 Element Plus 组件；主 chunk 从 769 kB 降到 58 kB。
- 千行文件拆成一两百行的可读单元，耦合点（props/emits、composable 返回值）显式化。
- 语音/SSE 这类易泄漏逻辑集中到 composable，清理入口唯一。

**代价与限制**

- 新增两个构建期依赖（`unplugin-auto-import`、`unplugin-vue-components`），首次 `npm install` 需要网络。
- **模板里的 `ElMessage` 不再有 import 语句**：新同学可能疑惑标识符从哪来——必须在
  `vite.config.js` 注释与本 ADR 里说明；若哪天换构建工具（非 Vite）需要重新配置。
- 按需引入依赖插件正确解析自定义组件与指令；漏解析不会构建失败，而是运行时"组件未注册"，
  因此改动后应至少跑一次 `vite build` 并打开页面确认（本次已用浏览器实测核心页面无未解析标签）。
- 拆分本身不减少总代码量（多了 props 声明），收益是可维护性；三个 view 的模板没有加单测，
  回归仍靠手工点击。

## 遗留与已知取舍（本次未处理）

- 三个 view 都没有单测，拆分后的回归靠浏览器手点；`useVoiceStream` 的 WebSocket / AudioContext 链路
  **没有实机回归**（需要麦克风与真实 ASR/TTS），只做了构建与静态核对。
- `ResumePanel` 内部直接调用了 `uploadResume` / `analyzeResume`（父组件仍持有 `resume` 状态并负责首次加载）。
  这让「子组件只做展示」的原则出现一个例外；若坚持所有 API 都留在父组件，父组件会回到约 340 行。
- 「展开全文」状态从父组件的 `expandedAnswers` 映射改为 `QuestionAnalysisItem` 内的局部 ref
  （element-plus 的 collapse-item 用 `v-show`，不销毁实例，行为一致；若将来开启惰性渲染会退化）。
- `SessionReport` 的 `playReplay` 产生的 `Audio` 对象仍不随卸载清理（保持原行为，未顺手改）。
- 未拆的 3 个 view：`WrittenTestExam.vue` 586、`Settings.vue` 586、`HistoryReport.vue` 479（大部分是 `<style>`）。

## 相关

- ADR-0007（无界资源收口：线程池/缓存/分页）
- 交付时注意：`backend/src/main/resources/static` 里的前端产物是旧版本，
  用 `build-release.cmd` / 手工 `vite build` + 复制 `dist` 才会更新后端内嵌的静态资源。
