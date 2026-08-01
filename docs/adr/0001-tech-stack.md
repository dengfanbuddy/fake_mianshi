# ADR-0001: 技术栈选型 — Spring Boot + Vue3 + SQLite

**Date:** 2026-08-01
**Status:** Accepted

## 背景

本项目是一个 AI 驱动的 Java 面试模拟系统，核心功能包括简历分析、AI 出题、笔试、语音模拟面试、面试分析与历史追踪。需要选择前端、后端、数据库技术栈。

## 决策

- **前端**：Vue3 + Element Plus
- **后端**：Spring Boot（Java）
- **数据库**：SQLite

## 考虑的方案

### 方案 A：全栈 Java（已选）
Spring Boot + Vue3 + SQLite。优势：项目本身可作为 Java 简历作品展示；AI 调用本质是 REST API，Java 完全胜任；单体架构个人项目够用。

### 方案 B：Python FastAPI + Vue3
Python 对 AI 集成更顺滑，开发速度快，但不能展示 Java 功力，与用户求职目标不匹配。

### 方案 C：混合 — Spring Boot 主后端 + Python AI 微服务
兼顾 Java 简历和 Python AI 效率，但两套服务部署复杂度高，个人项目过度设计。

## 理由

1. 用户是 Java 开发者，正在求职 Java 岗位。用 Spring Boot 构建本项目可一举两得：既是面试准备工具，又是简历项目。
2. AI 集成（DeepSeek API）、语音服务（腾讯云）均为 REST API 调用，与后端语言无关。
3. SQLite 免安装、零配置，适合个人项目本地运行，后续可平滑迁移至 MySQL/PostgreSQL。
4. 纯前端方案不可行：LLM API 不支持 CORS，API Key 不能暴露在前端，SQLite 需要后端进程。

## 后果

- AI 相关的流式处理在 Java 中不如 Python 原生异步方便，需用 Spring WebFlux 或 SSE 实现。
- SQLite 并发写入能力有限，但个人单用户场景不是问题。
- 后续如需多用户支持，需迁移数据库。
