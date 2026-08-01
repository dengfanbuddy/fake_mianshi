# AI 面试模拟系统

AI 驱动的 Java 面试训练工具：上传简历 → AI 分析 → 笔试 + 语音模拟面试 → 多维度分析报告 → 基于历史自适应出题。

## 技术栈

| 层 | 选型 |
|---|---|
| 前端 | Vue3 + Element Plus + Pinia + Vite |
| 后端 | Spring Boot 4.1.0 + Spring Data JPA |
| 数据库 | SQLite（免安装） |
| LLM | DeepSeek API（可在设置页切换） |
| 语音 | 腾讯云 ASR（STT）+ TTS |

## 快速启动

### 1. 启动后端（端口 8080）

```bash
cd backend
./mvnw.cmd spring-boot:run
```

首次运行自动：创建 SQLite 数据库 `data/fake_mianshi.db`、建表、插入 5 种预设面试官风格和默认 DeepSeek 配置。

> Windows Git Bash 下 `./mvnw`（bash 脚本）有 bug，必须用 `./mvnw.cmd`。

### 2. 启动前端（端口 5173）

```bash
cd frontend
npm install
npm run dev
```

打开 http://localhost:5173

### 3. 配置密钥

- **DeepSeek API Key**：打开前端「设置」页 → AI 模型配置 → 编辑默认的 deepseek 配置填入 API Key → 设为激活
- **腾讯云语音密钥**（STT/TTS）：以环境变量配置后端（不在前端展示）：
  - `TENCENT_SECRET_ID` / `TENCENT_SECRET_KEY` / `TENCENT_APP_ID`

## 使用流程

1. **首页** → 新建面试项目
2. **项目详情页** → 上传简历 PDF（自动 AI 分析）→ 确认职位需求（可手动填写或从简历生成）
3. **开始笔试** → 单选/多选/填空/简答，限时自动提交 → 查看结果
4. **开始模拟面试** → 选择面试官风格（技术深挖/压力/温和/项目实战/八股文，可自定义）→ 按住说话语音作答 → AI 面试官动态追问 → 中途可换面试官 → 自然收尾
5. **查看分析报告** → 单题分析（准确度/深度/流畅度/语气）+ 改进方案
6. **再次面试** → 系统根据历史弱点标签（JVM、并发等）优先出题
7. **历史综合分析** → 查看跨次面试的能力趋势与反复弱点

## 目录结构

```
├── backend/          # Spring Boot 后端
│   ├── src/main/java/com/fakemianshi/
│   │   ├── controller/   # REST API
│   │   ├── service/      # 业务逻辑（LLM、出题、面试、分析、语音）
│   │   ├── repository/   # JPA 数据访问
│   │   ├── entity/       # 13 个实体
│   │   └── config/       # CORS、异常、种子数据
│   └── src/test/         # 92 个测试
├── frontend/         # Vue3 前端
│   └── src/
│       ├── views/        # 笔试/面试/分析/项目/设置页面
│       ├── components/   # AudioRecorder 等
│       └── api/          # 接口封装
├── docs/
│   ├── adr/              # 架构决策记录
│   └── plans/            # 实现计划
└── CONTEXT.md            # 领域术语表
```

## 架构决策（ADR）

- `docs/adr/0001-tech-stack.md` — Spring Boot + Vue3 + SQLite（Java 简历加分）
- `docs/adr/0002-adaptive-question-generation.md` — 弱点标签 + LLM 混合出题
- `docs/adr/0003-voice-interaction.md` — 录音后发送 + 腾讯云语音

## 已知限制（MVP）

- 语音为录音后发送模式（非实时流式），后续可迭代
- 语气/情绪分析基于文本推断（未做音频信号分析）
- 录音保存为本地文件，未做云端存储
