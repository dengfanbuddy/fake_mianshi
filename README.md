# AI 面试模拟系统

AI 驱动的 Java 面试训练工具：上传简历 → AI 分析 → 笔试 + 语音模拟面试 → 多维度分析报告 → 基于历史自适应出题。

## 技术栈

| 层 | 选型 |
|---|---|
| 前端 | Vue3 + Element Plus（**按需引入**，`unplugin-vue-components`）+ Pinia + Vite |
| 后端 | Spring Boot 4.1.0 + MyBatis-Plus 3.5.16 |
| 数据库 | SQLite（免安装；WAL + `busy_timeout`） |
| LLM | DeepSeek API（可在设置页切换） |
| 语音 | TTS：腾讯云 API 或**本地 Qwen3-TTS sidecar**（设置页切换）；ASR：腾讯云 |

> 数据访问是 MyBatis-Plus 的 `BaseMapper`，**不是** JPA。`repository/` 下的接口是 Mapper。

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
- **语音**：
  - ASR（语音识别）必须走腾讯云：设置页填 SecretId / SecretKey / AppId（或环境变量
    `TENCENT_SECRET_ID` / `TENCENT_SECRET_KEY` / `TENCENT_APP_ID`）
  - TTS（语音合成）可选腾讯云或本地开源模型 Qwen3-TTS，见下节

### 4.（可选）启用本地 Qwen3-TTS 语音合成

无需密钥、无调用费用，音色更自然且能按人设描述语气。需要 NVIDIA GPU（0.6B 模型约 3–4GB 显存）。

```bash
cd voice-service
uv sync                                    # 首次约 2-3GB（torch CUDA 轮子）
pwsh -File scripts/download_model.ps1      # 从 ModelScope 下模型权重
pwsh -File run.ps1                         # 启动 sidecar，默认 http://127.0.0.1:9100
```

然后到「设置 → 语音服务配置」把供应商切到 **本地 Qwen3-TTS**，点「测试连通」，保存并激活。

自检（延迟/显存/音质）：`cd voice-service && uv run python selfcheck.py`，详见 `voice-service/README.md`。

## 测试与发布

```bash
# 后端测试（目前 128 个，全部通过）
cd backend && ./mvnw.cmd -o test

# 前端生产构建（产物在 frontend/dist）
cd frontend && npm run build
```

一键打发布包（前端构建 → 同步进后端 static → 后端 fat jar → jlink 精简 JRE → 组装目录）：

```bash
set JAVA_HOME=<JDK 17 路径>
build-release.cmd
```

产物在 `release/fake-mianshi/`，内含 `app.jar`、`start.cmd`、`jre/`，最终用户装了 Java 也不用装。

> `backend/src/main/resources/static/` 是 `frontend/dist` 的镜像副本，**已在 `backend/.gitignore` 中忽略**，不要往里手改东西——改了下次构建就没了。发布脚本用 `robocopy /MIR` 全量镜像，会自动清掉上一次构建遗留的哈希文件。

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
│   │   ├── controller/   # 12 个 REST 控制器
│   │   ├── service/      # 业务逻辑（LLM、出题、面试、分析、语音）
│   │   ├── repository/   # 15 个 MyBatis-Plus Mapper
│   │   ├── entity/       # 15 个实体
│   │   ├── dto/          # 出入参（含统一分页 PageResult）
│   │   ├── voice/        # 语音 WebSocket
│   │   └── config/       # CORS、异常、种子数据、有界线程池
│   └── src/test/         # 128 个测试
├── frontend/         # Vue3 前端
│   ├── vite.config.js    # 含 Element Plus 按需引入插件配置
│   └── src/
│       ├── views/        # 页面（笔试/面试/分析/项目/设置）
│       ├── components/   # 可复用组件，按域分目录：
│       │                 #   analysis/ mock-interview/ project/ + AudioRecorder
│       ├── composables/  # 有状态逻辑（分析加载、语音流、面试会话、人设切换）
│       └── api/          # 接口封装
├── voice-service/    # 本地 Qwen3-TTS sidecar（Python）
├── docs/
│   ├── adr/              # 架构决策记录
│   └── plans/            # 实现计划
└── CONTEXT.md            # 领域术语表
```

## 架构决策（ADR）

- `0001-tech-stack.md` — Spring Boot + Vue3 + SQLite（Java 简历加分）
- `0002-adaptive-question-generation.md` — 弱点标签 + LLM 混合出题
- `0003-voice-interaction.md` — 录音后发送 + 腾讯云 STT/TTS
- `0004-local-qwen-voice.md` — 语音本地化，本地 Qwen3-TTS sidecar
- `0005-llm-calls-outside-transactions.md` — LLM 调用一律在事务之外，只写库进短事务
- `0006-server-authoritative-exam-timing.md` — 笔试计时服务端裁决，超时只打标记不清空作答
- `0007-bounded-resources.md` — 会话缓存落库、线程池有界、列表分页与索引
- `0008-frontend-on-demand-and-splitting.md` — Element Plus 按需引入 + 巨型组件拆分

## 已知限制

- 语音为录音后发送模式（非实时流式），后续可迭代
- 语气/情绪分析基于文本推断（未做音频信号分析）
- 录音保存为本地文件，未做云端存储
- **无鉴权**：定位为本地单人使用；如需部署给他人使用，需先加登录与数据隔离
- 无错题本/薄弱点复习闭环：弱点标签目前只用于影响下一轮出题，没有独立的复习入口
- 面试报告不支持导出（仅页内查看）
