# 项目长期记忆 — fake_mianshi

## 项目概述
AI 驱动的 Java 面试模拟系统。用户是 Java 开发者（离职求职中），用此工具持续面试训练和查漏补缺。

## 技术栈
- 前端：Vue3 + Element Plus
- 后端：Spring Boot (Java)
- 数据库：SQLite
- LLM：DeepSeek API（可配置切换）
- STT/TTS：腾讯云 ASR + TTS
- 简历格式：PDF

## 核心设计决策
- 两阶段可独立：笔试（单选/多选/填空/简答，限时60分钟）+ 模拟面试（动态生成，不限时，语音对话）
- 面试官风格：预设+自定义，手动/随机/智能推荐，支持中途换人，影响 TTS
- 自适应出题：混合模式（弱点标签 + LLM），非完整知识图谱
- 语音交互：MVP 录音后发送模式，后续迭代实时流式
- 分析三层：单题分析 → 单次会话分析 → 历史综合分析
- 面试项目 = 按目标岗位分组管理

## 文档位置
- 领域术语表：CONTEXT.md
- ADR：docs/adr/
- Grilling 总结：docs/grilling-summary.md
