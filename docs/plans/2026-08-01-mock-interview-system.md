# AI 面试模拟系统 Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 构建一个 AI 驱动的 Java 面试模拟系统，支持简历分析、AI 出题、笔试、语音模拟面试、面试分析与历史追踪。

**Architecture:** 前后端分离单体架构。后端 Spring Boot 提供 REST API，前端 Vue3 SPA 调用。LLM（DeepSeek）和语音服务（腾讯云）通过后端代理调用。SQLite 存储业务数据，音频文件存文件系统。

**Tech Stack:** Java 17 + Spring Boot 3 + Spring Data JPA + SQLite | Vue3 + Element Plus + Pinia + Vite | DeepSeek API + 腾讯云 ASR/TTS

---

## 数据模型总览

```
InterviewProject (面试项目)
├── Resume (简历) — 1:1
├── PositionRequirement (职位需求) — 1:1
├── InterviewSession (面试会话) — 1:N
│   ├── WrittenTestQuestion/Answer (笔试题/答案) — 1:N
│   ├── MockInterviewMessage (面试对话消息) — 1:N
│   └── SessionAnalysis (会话分析) — 1:1
│       └── QuestionAnalysis (单题分析) — 1:N
├── WeaknessTag (弱点标签) — 1:N
└── HistoricalAnalysis (历史综合分析) — 1:N

InterviewerPersona (面试官风格) — 全局
AIModelConfig (AI模型配置) — 全局
```

---

## Phase 1: 项目骨架与基础设施

### Task 1: 初始化 Spring Boot 后端项目

**Files:**
- Create: `backend/pom.xml`
- Create: `backend/src/main/resources/application.yml`
- Create: `backend/src/main/java/com/fakemianshi/FakeMianshiApplication.java`
- Create: `backend/src/test/java/com/fakemianshi/FakeMianshiApplicationTest.java`

**Step 1: 创建 pom.xml**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
         https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.2.5</version>
    </parent>
    <groupId>com.fakemianshi</groupId>
    <artifactId>fake-mianshi-backend</artifactId>
    <version>0.0.1-SNAPSHOT</version>
    <name>fake-mianshi-backend</name>

    <properties>
        <java.version>17</java.version>
    </properties>

    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-jpa</artifactId>
        </dependency>
        <dependency>
            <groupId>org.xerial</groupId>
            <artifactId>sqlite-jdbc</artifactId>
            <version>3.45.1.0</version>
        </dependency>
        <dependency>
            <groupId>org.hibernate.orm</groupId>
            <artifactId>hibernate-community-dialects</artifactId>
        </dependency>
        <dependency>
            <groupId>org.apache.pdfbox</groupId>
            <artifactId>pdfbox</artifactId>
            <version>3.0.1</version>
        </dependency>
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <optional>true</optional>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
```

**Step 2: 创建 application.yml**

```yaml
server:
  port: 8080

spring:
  datasource:
    url: jdbc:sqlite:./data/fake_mianshi.db
    driver-class-name: org.sqlite.JDBC
  jpa:
    database-platform: org.hibernate.community.dialect.SQLiteDialect
    hibernate:
      ddl-auto: update
    show-sql: false
  servlet:
    multipart:
      max-file-size: 10MB
      max-request-size: 50MB

app:
  upload-dir: ./uploads
  audio-dir: ./uploads/audio
```

**Step 3: 创建主启动类**

```java
package com.fakemianshi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class FakeMianshiApplication {
    public static void main(String[] args) {
        SpringApplication.run(FakeMianshiApplication.class, args);
    }
}
```

**Step 4: 创建测试类**

```java
package com.fakemianshi;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class FakeMianshiApplicationTest {
    @Test
    void contextLoads() {
    }
}
```

**Step 5: 运行测试验证项目可启动**

```bash
cd backend && mvn test
```
Expected: BUILD SUCCESS

**Step 6: Commit**

```bash
git add -A && git commit -m "feat: initialize Spring Boot backend project"
```

---

### Task 2: 初始化 Vue3 前端项目

**Files:**
- Create: `frontend/` (vite scaffolded project)

**Step 1: 用 Vite 创建 Vue3 项目**

```bash
cd E:/project2/AI/fake_mianshi
npm create vite@latest frontend -- --template vue
cd frontend && npm install
npm install element-plus vue-router@4 pinia axios
```

**Step 2: 配置 vite.config.js 代理后端**

```javascript
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true
      }
    }
  }
})
```

**Step 3: 创建项目目录结构**

```bash
mkdir -p src/{api,router,store,views,components,utils,assets}
```

**Step 4: 创建 axios 实例 `src/api/request.js`**

```javascript
import axios from 'axios'
import { ElMessage } from 'element-plus'

const request = axios.create({
  baseURL: '/api',
  timeout: 60000
})

request.interceptors.response.use(
  response => response.data,
  error => {
    ElMessage.error(error.response?.data?.message || '请求失败')
    return Promise.reject(error)
  }
)

export default request
```

**Step 5: 验证前端可启动**

```bash
npm run dev
```
Expected: Vite dev server running on http://localhost:5173

**Step 6: Commit**

```bash
git add -A && git commit -m "feat: initialize Vue3 frontend project"
```

---

### Task 3: 后端包结构与 CORS 配置

**Files:**
- Create: `backend/src/main/java/com/fakemianshi/config/CorsConfig.java`
- Create: `backend/src/main/java/com/fakemianshi/config/WebConfig.java`
- Create: directories: `controller/`, `service/`, `repository/`, `entity/`, `dto/`, `util/`

**Step 1: 创建 CORS 配置**

```java
package com.fakemianshi.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

@Configuration
public class CorsConfig {
    @Bean
    public CorsFilter corsFilter() {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        CorsConfiguration config = new CorsConfiguration();
        config.addAllowedOrigin("http://localhost:5173");
        config.addAllowedHeader("*");
        config.addAllowedMethod("*");
        source.registerCorsConfiguration("/api/**", config);
        return new CorsFilter(source);
    }
}
```

**Step 2: 创建全局异常处理 `config/GlobalExceptionHandler.java`**

```java
package com.fakemianshi.config;

import com.fakemianshi.dto.ApiResponse;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(Exception.class)
    public ApiResponse<?> handleException(Exception e) {
        return ApiResponse.error(e.getMessage());
    }
}
```

**Step 3: 创建统一响应 DTO `dto/ApiResponse.java`**

```java
package com.fakemianshi.dto;

import lombok.Data;

@Data
public class ApiResponse<T> {
    private int code;
    private String message;
    private T data;

    public static <T> ApiResponse<T> success(T data) {
        ApiResponse<T> r = new ApiResponse<>();
        r.code = 200;
        r.message = "success";
        r.data = data;
        return r;
    }

    public static <T> ApiResponse<T> error(String message) {
        ApiResponse<T> r = new ApiResponse<>();
        r.code = 500;
        r.message = message;
        return r;
    }
}
```

**Step 4: 设置所有 Controller 路径前缀 `/api` — 在 application.yml 添加**

```yaml
server:
  servlet:
    context-path: /api
```

**Step 5: 运行测试**

```bash
cd backend && mvn test
```
Expected: BUILD SUCCESS

**Step 6: Commit**

```bash
git add -A && git commit -m "feat: add CORS config, global exception handler, ApiResponse DTO"
```

---

### Task 4: 数据库实体定义

**Files:**
- Create: `entity/InterviewProject.java`
- Create: `entity/Resume.java`
- Create: `entity/PositionRequirement.java`
- Create: `entity/InterviewSession.java`
- Create: `entity/WrittenTestQuestion.java`
- Create: `entity/WrittenTestAnswer.java`
- Create: `entity/MockInterviewMessage.java`
- Create: `entity/InterviewerPersona.java`
- Create: `entity/SessionAnalysis.java`
- Create: `entity/QuestionAnalysis.java`
- Create: `entity/WeaknessTag.java`
- Create: `entity/HistoricalAnalysis.java`
- Create: `entity/AIModelConfig.java`

**Step 1: 创建 InterviewProject 实体**

```java
package com.fakemianshi.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Entity
public class InterviewProject {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
    private String description;

    @OneToMany(mappedBy = "projectId", cascade = CascadeType.ALL)
    @OrderBy("createdAt DESC")
    private List<InterviewSession> sessions;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
```

**Step 2: 创建 Resume 实体**

```java
package com.fakemianshi.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
public class Resume {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long projectId;
    private String filePath;
    private String originalFilename;
    @Column(length = 50000)
    private String parsedText;
    @Column(length = 50000)
    private String analysisResult; // JSON

    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        createdAt = LocalDateTime.now();
    }
}
```

**Step 3: 创建 PositionRequirement 实体**

```java
package com.fakemianshi.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
public class PositionRequirement {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long projectId;
    private String jobTitle;
    private String experience;
    private String seniority;       // 初级/中级/高级
    private String companyType;    // 大厂/创业公司/外企
    @Column(length = 5000)
    private String techStack;      // JSON array
    @Column(length = 10000)
    private String jobDescription;
    private String source;         // RESUME / MANUAL

    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        createdAt = LocalDateTime.now();
    }
}
```

**Step 4: 创建 InterviewSession 实体**

```java
package com.fakemianshi.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
public class InterviewSession {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long projectId;
    private Long positionRequirementId;
    private String type;        // WRITTEN / MOCK / BOTH
    private String status;      // PENDING / IN_PROGRESS / COMPLETED / ABANDONED
    private Integer timeLimit;  // minutes, null for mock interview

    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        createdAt = LocalDateTime.now();
    }
}
```

**Step 5: 创建 WrittenTestQuestion 和 WrittenTestAnswer 实体**

```java
package com.fakemianshi.entity;

import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
public class WrittenTestQuestion {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long sessionId;
    private String type;        // SINGLE_CHOICE / MULTIPLE_CHOICE / FILL_BLANK / SHORT_ANSWER
    @Column(length = 5000)
    private String content;
    @Column(length = 10000)
    private String options;     // JSON array, null for FILL_BLANK/SHORT_ANSWER
    @Column(length = 5000)
    private String answer;
    @Column(length = 5000)
    private String explanation;
    @Column(length = 2000)
    private String knowledgePoints; // JSON array
    private Integer orderNum;
}

@Data
@Entity
public class WrittenTestAnswer {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long questionId;
    private Long sessionId;
    @Column(length = 5000)
    private String userAnswer;
    private Boolean isCorrect;
    private Double score;
}
```

**Step 6: 创建 MockInterviewMessage 实体**

```java
package com.fakemianshi.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
public class MockInterviewMessage {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long sessionId;
    private String role;        // INTERVIEWER / CANDIDATE
    @Column(length = 10000)
    private String content;
    private String audioPath;   // 音频文件路径
    private Long personaId;     // 当前面试官风格 ID
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        createdAt = LocalDateTime.now();
    }
}
```

**Step 7: 创建 InterviewerPersona 实体**

```java
package com.fakemianshi.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
public class InterviewerPersona {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
    @Column(length = 2000)
    private String description;
    @Column(length = 5000)
    private String styleConfig;  // JSON: tone, speed, aggressiveness, followupStrategy
    private Boolean isPreset;
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        createdAt = LocalDateTime.now();
    }
}
```

**Step 8: 创建 SessionAnalysis, QuestionAnalysis 实体**

```java
package com.fakemianshi.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
public class SessionAnalysis {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long sessionId;
    private Double overallScore;
    @Column(length = 5000)
    private String strengths;       // JSON array
    @Column(length = 5000)
    private String weaknesses;      // JSON array
    @Column(length = 5000)
    private String knowledgeGaps;    // JSON array
    @Column(length = 5000)
    private String communicationEvaluation;
    @Column(length = 10000)
    private String improvementPlan;  // JSON
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        createdAt = LocalDateTime.now();
    }
}

@Data
@Entity
public class QuestionAnalysis {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long sessionId;
    private Long questionRefId;     // WrittenTestQuestion.id or MockInterviewMessage.id
    @Column(length = 5000)
    private String questionContent;
    @Column(length = 10000)
    private String answerContent;
    private Double accuracy;
    private Double depth;
    private Double clarity;
    private Double fluency;
    private String toneEvaluation;
    @Column(length = 5000)
    private String improvementSuggestion;
}
```

**Step 9: 创建 WeaknessTag, HistoricalAnalysis, AIModelConfig 实体**

```java
package com.fakemianshi.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
public class WeaknessTag {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long projectId;
    private String knowledgePoint;
    private String masteryLevel;     // WEAK / FAIR / GOOD / STRONG
    private Integer occurrenceCount;
    private Long lastSessionId;
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        updatedAt = LocalDateTime.now();
    }
}

@Data
@Entity
public class HistoricalAnalysis {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long projectId;
    @Column(length = 20000)
    private String analysisData;     // JSON
    private LocalDateTime generatedAt;

    @PrePersist
    public void prePersist() {
        generatedAt = LocalDateTime.now();
    }
}

@Data
@Entity
public class AIModelConfig {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String provider;       // deepseek / openai / etc.
    private String apiUrl;
    private String apiKey;
    private String modelName;
    private Boolean isActive;
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        createdAt = LocalDateTime.now();
    }
}
```

**Step 10: 运行测试验证实体映射**

```bash
cd backend && mvn test
```
Expected: BUILD SUCCESS, SQLite 数据库文件自动创建

**Step 11: Commit**

```bash
git add -A && git commit -m "feat: define all JPA entities for data model"
```

---

### Task 5: Repository 接口

**Files:**
- Create: `repository/*.java` (每个实体一个)

**Step 1: 为每个实体创建 JpaRepository**

每个 repository 接口模式相同，以 InterviewProjectRepository 为例：

```java
package com.fakemianshi.repository;

import com.fakemianshi.entity.InterviewProject;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface InterviewProjectRepository extends JpaRepository<InterviewProject, Long> {
    List<InterviewProject> findAllByOrderByCreatedAtDesc();
}
```

按同样模式创建：
- `ResumeRepository` — `findByProjectId(Long projectId)`
- `PositionRequirementRepository` — `findByProjectId(Long projectId)`
- `InterviewSessionRepository` — `findByProjectIdOrderByCreatedAtDesc(Long projectId)`
- `WrittenTestQuestionRepository` — `findBySessionIdOrderByOrderNum(Long sessionId)`
- `WrittenTestAnswerRepository` — `findBySessionId(Long sessionId)`
- `MockInterviewMessageRepository` — `findBySessionIdOrderByCreatedAt(Long sessionId)`
- `InterviewerPersonaRepository` — `findByIsPreset(Boolean isPreset)`
- `SessionAnalysisRepository` — `findBySessionId(Long sessionId)`
- `QuestionAnalysisRepository` — `findBySessionId(Long sessionId)`
- `WeaknessTagRepository` — `findByProjectId(Long projectId)`
- `HistoricalAnalysisRepository` — `findByProjectIdOrderByGeneratedAtDesc(Long projectId)`
- `AIModelConfigRepository` — `findByIsActiveTrue()`

**Step 2: 测试编译通过**

```bash
cd backend && mvn compile
```
Expected: BUILD SUCCESS

**Step 3: Commit**

```bash
git add -A && git commit -m "feat: add JPA repository interfaces"
```

---

## Phase 2: AI 模型集成

### Task 6: LLM API 客户端

**Files:**
- Create: `service/LlmService.java`
- Create: `service/impl/LlmServiceImpl.java`
- Create: `dto/LlmRequest.java`
- Create: `dto/LlmResponse.java`
- Test: `src/test/java/com/fakemianshi/service/LlmServiceTest.java`

**Step 1: 创建 LLM 请求/响应 DTO**

```java
package com.fakemianshi.dto;

import lombok.Data;
import java.util.List;

@Data
public class LlmRequest {
    private String model;
    private List<LlmMessage> messages;
    private Double temperature;
    private Integer maxTokens;
    private Boolean stream;

    @Data
    public static class LlmMessage {
        private String role;    // system / user / assistant
        private String content;
    }
}
```

```java
package com.fakemianshi.dto;

import lombok.Data;

@Data
public class LlmResponse {
    private String content;
    private String finishReason;
    private Integer totalTokens;
}
```

**Step 2: 创建 LlmService 接口**

```java
package com.fakemianshi.service;

import com.fakemianshi.dto.LlmResponse;
import java.util.List;

public interface LlmService {
    LlmResponse chat(String systemPrompt, String userPrompt);
    LlmResponse chat(List<LlmService.Message> messages);

    record Message(String role, String content) {}
}
```

**Step 3: 实现 LlmServiceImpl — 调用 DeepSeek API**

```java
package com.fakemianshi.service.impl;

import com.fakemianshi.dto.LlmRequest;
import com.fakemianshi.dto.LlmResponse;
import com.fakemianshi.entity.AIModelConfig;
import com.fakemianshi.repository.AIModelConfigRepository;
import com.fakemianshi.service.LlmService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class LlmServiceImpl implements LlmService {

    private final AIModelConfigRepository configRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestTemplate restTemplate = new RestTemplate();

    @Override
    public LlmResponse chat(String systemPrompt, String userPrompt) {
        List<Message> messages = new ArrayList<>();
        messages.add(new Message("system", systemPrompt));
        messages.add(new Message("user", userPrompt));
        return chat(messages);
    }

    @Override
    public LlmResponse chat(List<Message> messages) {
        AIModelConfig config = configRepository.findByIsActiveTrue()
                .orElseThrow(() -> new RuntimeException("No active AI model configured"));

        LlmRequest request = new LlmRequest();
        request.setModel(config.getModelName());
        request.setMessages(messages.stream().map(m -> {
            LlmRequest.LlmMessage msg = new LlmRequest.LlmMessage();
            msg.setRole(m.role());
            msg.setContent(m.content());
            return msg;
        }).toList());
        request.setTemperature(0.7);
        request.setMaxTokens(4096);
        request.setStream(false);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(config.getApiKey());

        try {
            String jsonBody = objectMapper.writeValueAsString(request);
            HttpEntity<String> entity = new HttpEntity<>(jsonBody, headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    config.getApiUrl(),
                    HttpMethod.POST,
                    entity,
                    String.class
            );

            JsonNode root = objectMapper.readTree(response.getBody());
            JsonNode choices = root.path("choices");
            if (choices.isArray() && !choices.isEmpty()) {
                JsonNode messageNode = choices.get(0).path("message").path("content");
                LlmResponse llmResponse = new LlmResponse();
                llmResponse.setContent(messageNode.asText());
                llmResponse.setTotalTokens(root.path("usage").path("total_tokens").asInt());
                return llmResponse;
            }
            throw new RuntimeException("LLM returned no choices");
        } catch (Exception e) {
            log.error("LLM API call failed", e);
            throw new RuntimeException("LLM API call failed: " + e.getMessage());
        }
    }
}
```

**Step 4: 写测试 — mock configRepository，验证请求构建**

```java
package com.fakemianshi.service;

import com.fakemianshi.entity.AIModelConfig;
import com.fakemianshi.repository.AIModelConfigRepository;
import com.fakemianshi.service.impl.LlmServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mockito;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LlmServiceTest {

    private AIModelConfigRepository configRepository;
    private LlmServiceImpl llmService;

    @BeforeEach
    void setUp() {
        configRepository = mock(AIModelConfigRepository.class);
        llmService = new LlmServiceImpl(configRepository);
    }

    @Test
    void chat_throwsWhenNoActiveConfig() {
        when(configRepository.findByIsActiveTrue()).thenReturn(Optional.empty());
        assertThrows(RuntimeException.class, () ->
            llmService.chat("system", "user"));
    }

    @Test
    void chat_buildsCorrectMessages() {
        // 验证 system + user 被正确组装为 2 条消息
        // (此测试验证消息构建逻辑，实际 API 调用需要 mock RestTemplate)
    }
}
```

**Step 5: 运行测试**

```bash
cd backend && mvn test -Dtest=LlmServiceTest
```
Expected: Tests pass

**Step 6: Commit**

```bash
git add -A && git commit -m "feat: add LLM service with DeepSeek API integration"
```

---

### Task 7: AI 模型配置 CRUD API

**Files:**
- Create: `controller/AIModelConfigController.java`
- Create: `service/AIModelConfigService.java`

**Step 1: 创建 service**

```java
package com.fakemianshi.service;

import com.fakemianshi.entity.AIModelConfig;
import java.util.List;

public interface AIModelConfigService {
    List<AIModelConfig> findAll();
    AIModelConfig save(AIModelConfig config);
    AIModelConfig setActive(Long id);
}
```

**Step 2: 实现 service — save 时确保只有一个 active**

```java
package com.fakemianshi.service.impl;

import com.fakemianshi.entity.AIModelConfig;
import com.fakemianshi.repository.AIModelConfigRepository;
import com.fakemianshi.service.AIModelConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AIModelConfigServiceImpl implements AIModelConfigService {

    private final AIModelConfigRepository repository;

    @Override
    public List<AIModelConfig> findAll() {
        return repository.findAll();
    }

    @Override
    public AIModelConfig save(AIModelConfig config) {
        if (config.getIsActive() == null) config.setIsActive(false);
        if (Boolean.TRUE.equals(config.getIsActive())) {
            repository.findAll().forEach(c -> {
                c.setIsActive(false);
                repository.save(c);
            });
        }
        return repository.save(config);
    }

    @Override
    public AIModelConfig setActive(Long id) {
        repository.findAll().forEach(c -> {
            c.setIsActive(false);
            repository.save(c);
        });
        AIModelConfig config = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Config not found"));
        config.setIsActive(true);
        return repository.save(config);
    }
}
```

**Step 3: 创建 controller**

```java
package com.fakemianshi.controller;

import com.fakemianshi.dto.ApiResponse;
import com.fakemianshi.entity.AIModelConfig;
import com.fakemianshi.service.AIModelConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/ai-config")
@RequiredArgsConstructor
public class AIModelConfigController {

    private final AIModelConfigService service;

    @GetMapping
    public ApiResponse<List<AIModelConfig>> list() {
        return ApiResponse.success(service.findAll());
    }

    @PostMapping
    public ApiResponse<AIModelConfig> save(@RequestBody AIModelConfig config) {
        return ApiResponse.success(service.save(config));
    }

    @PutMapping("/{id}/active")
    public ApiResponse<AIModelConfig> setActive(@PathVariable Long id) {
        return ApiResponse.success(service.setActive(id));
    }
}
```

**Step 4: 测试 — MockMvc 集成测试**

```java
package com.fakemianshi.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fakemianshi.entity.AIModelConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class AIModelConfigControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @Test
    void list_returnsOk() throws Exception {
        mockMvc.perform(get("/ai-config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }
}
```

**Step 5: 运行测试**

```bash
cd backend && mvn test -Dtest=AIModelConfigControllerTest
```
Expected: Tests pass

**Step 6: Commit**

```bash
git add -A && git commit -m "feat: add AI model config CRUD API"
```

---

## Phase 3: 简历模块

### Task 8: 简历上传与 PDF 解析

**Files:**
- Create: `controller/ResumeController.java`
- Create: `service/ResumeService.java`
- Create: `service/impl/ResumeServiceImpl.java`
- Create: `util/PdfUtil.java`

**Step 1: 创建 PDF 文本提取工具**

```java
package com.fakemianshi.util;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import java.io.File;

public class PdfUtil {
    public static String extractText(File pdfFile) {
        try (PDDocument document = Loader.loadPDF(pdfFile)) {
            PDFTextStripper stripper = new PDFTextStripper();
            return stripper.getText(document);
        } catch (Exception e) {
            throw new RuntimeException("PDF text extraction failed: " + e.getMessage());
        }
    }
}
```

**Step 2: 创建 ResumeService 接口**

```java
package com.fakemianshi.service;

import com.fakemianshi.entity.Resume;
import org.springframework.web.multipart.MultipartFile;

public interface ResumeService {
    Resume uploadAndParse(Long projectId, MultipartFile file);
    Resume getByProjectId(Long projectId);
    Resume analyze(Long resumeId);  // LLM 分析简历
}
```

**Step 3: 实现 ResumeServiceImpl**

```java
package com.fakemianshi.service.impl;

import com.fakemianshi.entity.Resume;
import com.fakemianshi.repository.ResumeRepository;
import com.fakemianshi.service.LlmService;
import com.fakemianshi.service.ResumeService;
import com.fakemianshi.util.PdfUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Slf4j
@Service
@RequiredArgsConstructor
public class ResumeServiceImpl implements ResumeService {

    private final ResumeRepository resumeRepository;
    private final LlmService llmService;

    @Value("${app.upload-dir}")
    private String uploadDir;

    @Override
    public Resume uploadAndParse(Long projectId, MultipartFile file) {
        try {
            Path dir = Paths.get(uploadDir, "resumes");
            Files.createDirectories(dir);
            String filename = System.currentTimeMillis() + "_" + file.getOriginalFilename();
            Path filePath = dir.resolve(filename);
            file.transferTo(filePath.toFile());

            String parsedText = PdfUtil.extractText(filePath.toFile());

            Resume resume = new Resume();
            resume.setProjectId(projectId);
            resume.setFilePath(filePath.toString());
            resume.setOriginalFilename(file.getOriginalFilename());
            resume.setParsedText(parsedText);
            return resumeRepository.save(resume);
        } catch (IOException e) {
            throw new RuntimeException("Resume upload failed: " + e.getMessage());
        }
    }

    @Override
    public Resume getByProjectId(Long projectId) {
        return resumeRepository.findByProjectId(projectId)
                .stream()
                .reduce((first, second) -> second)  // 取最新一条
                .orElse(null);
    }

    @Override
    public Resume analyze(Long resumeId) {
        Resume resume = resumeRepository.findById(resumeId)
                .orElseThrow(() -> new RuntimeException("Resume not found"));

        String systemPrompt = """
            你是一个专业的招聘分析专家。请分析以下简历内容，提取结构化信息，以 JSON 格式返回：
            {
              "experienceYears": 数字,
              "currentPosition": "当前职位",
              "techStack": ["技术1", "技术2"],
              "projects": [
                {"name": "项目名", "role": "角色", "description": "描述", "tech": ["技术"]}
              ],
              "education": "学历信息",
              "suggestedPosition": "建议面试岗位",
              "suggestedSeniority": "建议资历级别(初级/中级/高级)"
            }
            """;

        LlmService.LlmResponse response = llmService.chat(systemPrompt, resume.getParsedText());
        resume.setAnalysisResult(response.getContent());
        return resumeRepository.save(resume);
    }
}
```

**Step 4: 创建 ResumeController**

```java
package com.fakemianshi.controller;

import com.fakemianshi.dto.ApiResponse;
import com.fakemianshi.entity.Resume;
import com.fakemianshi.service.ResumeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/resume")
@RequiredArgsConstructor
public class ResumeController {

    private final ResumeService resumeService;

    @PostMapping("/upload/{projectId}")
    public ApiResponse<Resume> upload(@PathVariable Long projectId,
                                       @RequestParam("file") MultipartFile file) {
        return ApiResponse.success(resumeService.uploadAndParse(projectId, file));
    }

    @GetMapping("/{projectId}")
    public ApiResponse<Resume> getByProject(@PathVariable Long projectId) {
        return ApiResponse.success(resumeService.getByProjectId(projectId));
    }

    @PostMapping("/analyze/{resumeId}")
    public ApiResponse<Resume> analyze(@PathVariable Long resumeId) {
        return ApiResponse.success(resumeService.analyze(resumeId));
    }
}
```

**Step 5: 测试 — 验证 PDF 解析逻辑（准备一个测试 PDF）**

```java
package com.fakemianshi.util;

import org.junit.jupiter.api.Test;
import java.io.File;
import static org.junit.jupiter.api.Assertions.*;

class PdfUtilTest {
    @Test
    void extractText_fromNonExistentFile_throws() {
        assertThrows(RuntimeException.class, () ->
            PdfUtil.extractText(new File("nonexistent.pdf")));
    }
}
```

**Step 6: 运行测试**

```bash
cd backend && mvn test -Dtest=PdfUtilTest
```
Expected: Test passes (验证异常处理)

**Step 7: Commit**

```bash
git add -A && git commit -m "feat: add resume upload, PDF parsing, and LLM analysis"
```

---

## Phase 4: 项目管理与职位需求

### Task 9: 面试项目 CRUD

**Files:**
- Create: `controller/ProjectController.java`
- Create: `service/ProjectService.java`
- Create: `service/impl/ProjectServiceImpl.java`

**Step 1: 创建 ProjectService 和实现**

```java
package com.fakemianshi.service;

import com.fakemianshi.entity.InterviewProject;
import java.util.List;

public interface ProjectService {
    List<InterviewProject> findAll();
    InterviewProject findById(Long id);
    InterviewProject create(InterviewProject project);
    InterviewProject update(Long id, InterviewProject project);
    void delete(Long id);
}
```

实现类按标准 CRUD 模式，`findAll` 返回按创建时间倒序列表。`delete` 时级联删除简历、会话等关联数据。

**Step 2: 创建 ProjectController**

```java
@RestController
@RequestMapping("/project")
@RequiredArgsConstructor
public class ProjectController {
    private final ProjectService projectService;

    @GetMapping
    public ApiResponse<List<InterviewProject>> list() {
        return ApiResponse.success(projectService.findAll());
    }

    @GetMapping("/{id}")
    public ApiResponse<InterviewProject> get(@PathVariable Long id) {
        return ApiResponse.success(projectService.findById(id));
    }

    @PostMapping
    public ApiResponse<InterviewProject> create(@RequestBody InterviewProject project) {
        return ApiResponse.success(projectService.create(project));
    }

    @PutMapping("/{id}")
    public ApiResponse<InterviewProject> update(@PathVariable Long id, @RequestBody InterviewProject project) {
        return ApiResponse.success(projectService.update(id, project));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        projectService.delete(id);
        return ApiResponse.success(null);
    }
}
```

**Step 3: 测试 — MockMvc CRUD 集成测试**

```java
@SpringBootTest
@AutoConfigureMockMvc
class ProjectControllerTest {
    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @Test
    void createProject_returnsOk() throws Exception {
        String body = "{\"name\":\"Java后端高级\",\"description\":\"准备大厂面试\"}";
        mockMvc.perform(post("/project")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Java后端高级"));
    }

    @Test
    void listProjects_returnsOk() throws Exception {
        mockMvc.perform(get("/project"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }
}
```

**Step 4: 运行测试 → Commit**

```bash
cd backend && mvn test -Dtest=ProjectControllerTest
git add -A && git commit -m "feat: add interview project CRUD API"
```

---

### Task 10: 职位需求管理

**Files:**
- Create: `controller/PositionRequirementController.java`
- Create: `service/PositionRequirementService.java`

**Step 1: 创建 service 和 controller**

Service 支持：
- `saveOrUpdate(Long projectId, PositionRequirement req)` — 保存或更新
- `getByProjectId(Long projectId)` — 查询
- `createFromResume(Long projectId)` — 从简历分析结果自动生成职位需求

```java
// PositionRequirementServiceImpl 关键方法
public PositionRequirement createFromResume(Long projectId) {
    Resume resume = resumeService.getByProjectId(projectId);
    if (resume == null || resume.getAnalysisResult() == null) {
        throw new RuntimeException("请先上传并分析简历");
    }
    // 解析 resume.analysisResult JSON，提取 suggestedPosition 和 suggestedSeniority
    // 构建 PositionRequirement，source = "RESUME"
    // 返回让用户确认
}
```

Controller:
- `GET /position/{projectId}` — 获取职位需求
- `POST /position/{projectId}` — 保存/更新
- `POST /position/{projectId}/from-resume` — 从简历生成

**Step 2: 测试 → Commit**

```bash
cd backend && mvn test -Dtest=PositionRequirement*
git add -A && git commit -m "feat: add position requirement management"
```

---

## Phase 5: 出题引擎

### Task 11: 笔试题生成

**Files:**
- Create: `service/QuestionGenerationService.java`
- Create: `service/impl/QuestionGenerationServiceImpl.java`

**Step 1: 创建出题服务接口**

```java
public interface QuestionGenerationService {
    List<WrittenTestQuestion> generateWrittenTestQuestions(
        Long sessionId, Long projectId, int questionCount);
    String generateMockInterviewOutline(
        Long projectId, Long personaId);
}
```

**Step 2: 实现笔试题生成 — LLM prompt 设计**

```java
@Override
public List<WrittenTestQuestion> generateWrittenTestQuestions(
        Long sessionId, Long projectId, int questionCount) {

    PositionRequirement position = positionService.getByProjectId(projectId);
    Resume resume = resumeService.getByProjectId(projectId);
    List<WeaknessTag> weaknessTags = weaknessTagRepository.findByProjectId(projectId);

    String systemPrompt = """
        你是一个 Java 面试题生成器。根据职位需求、简历信息和弱点标签，生成 %d 道笔试题。
        题型分配：单选题(30%%)、多选题(20%%)、填空题(20%%)、简答题(30%%)。
        以 JSON 数组返回，每题格式：
        {"type":"SINGLE_CHOICE","content":"题干","options":["A","B","C","D"],
         "answer":"A","explanation":"解析","knowledgePoints":["知识点"]}
        填空题和简答题 options 为 null。
        """.formatted(questionCount);

    String userPrompt = buildContext(position, resume, weaknessTags);

    LlmService.LlmResponse response = llmService.chat(systemPrompt, userPrompt);
    // 解析 JSON 数组 → 构建 WrittenTestQuestion 实体列表
    return parseQuestions(response.getContent(), sessionId);
}
```

**Step 3: 实现上下文构建方法 `buildContext`**

将职位需求、简历项目经历、弱点标签组装为给 LLM 的 user prompt。弱点标签提示"以下知识点用户较弱，优先出题"。

**Step 4: 实现面试大纲生成 `generateMockInterviewOutline`**

```java
@Override
public String generateMockInterviewOutline(Long projectId, Long personaId) {
    // 构建 system prompt：你是 [面试官风格]，请生成面试大纲
    // 包含：技术知识点覆盖、简历项目深挖、少量行为题
    // 返回大纲文本（不是具体题目，面试中动态生成）
}
```

**Step 5: 测试 — mock LlmService，验证 prompt 构建和 JSON 解析**

```java
@Test
void generateWrittenTestQuestions_parsesLlmResponse() {
    // mock llmService.chat 返回模拟 JSON
    // 验证解析出的题目数量和字段正确
}
```

**Step 6: 运行测试 → Commit**

```bash
cd backend && mvn test -Dtest=QuestionGeneration*
git add -A && git commit -m "feat: add question generation service for written test and mock interview"
```

---

## Phase 6: 笔试模块

### Task 12: 笔试会话管理

**Files:**
- Create: `controller/WrittenTestController.java`
- Create: `service/WrittenTestService.java`

**Step 1: 创建笔试 service**

```java
public interface WrittenTestService {
    // 开始笔试：创建 session + 生成题目
    WrittenTestStartResponse start(Long projectId, int timeLimit, int questionCount);
    // 提交笔试：评分
    WrittenTestSubmitResponse submit(Long sessionId, Map<Long, String> answers);
    // 获取笔试详情
    WrittenTestDetailResponse getDetail(Long sessionId);
}
```

**Step 2: 实现关键逻辑**

- `start`: 创建 InterviewSession(type=WRITTEN, status=IN_PROGRESS)，调用 QuestionGenerationService 生成题目，保存到 DB
- `submit`: 遍历每题，对比答案，计算 isCorrect 和 score，更新 session status=COMPLETED
- `getDetail`: 返回题目列表 + 用户答案 + 对错

**Step 3: 创建 controller**

```java
@RestController
@RequestMapping("/written-test")
public class WrittenTestController {
    @PostMapping("/start/{projectId}")
    public ApiResponse<WrittenTestStartResponse> start(...) {}

    @PostMapping("/submit/{sessionId}")
    public ApiResponse<WrittenTestSubmitResponse> submit(...) {}

    @GetMapping("/{sessionId}")
    public ApiResponse<WrittenTestDetailResponse> getDetail(...) {}
}
```

**Step 4: 测试 → Commit**

```bash
cd backend && mvn test -Dtest=WrittenTest*
git add -A && git commit -m "feat: add written test session management and scoring"
```

---

### Task 13: 笔试前端 UI

**Files:**
- Create: `frontend/src/views/written-test/WrittenTestStart.vue`
- Create: `frontend/src/views/written-test/WrittenTestExam.vue`
- Create: `frontend/src/views/written-test/WrittenTestResult.vue`
- Create: `frontend/src/api/written-test.js`

**Step 1: 创建 API 模块**

```javascript
import request from './request'

export const startTest = (projectId, params) =>
  request.post(`/written-test/start/${projectId}`, null, { params })

export const submitTest = (sessionId, answers) =>
  request.post(`/written-test/submit/${sessionId}`, answers)

export const getTestDetail = (sessionId) =>
  request.get(`/written-test/${sessionId}`)
```

**Step 2: 创建考试界面 WrittenTestExam.vue**

关键功能：
- 显示题目列表（单选/多选/填空/简答）
- 顶部倒计时（从 timeLimit 计算）
- 答题卡导航（题号 + 已答/未答状态）
- 提交按钮 + 时间到自动提交

```vue
<template>
  <div class="written-test">
    <el-card>
      <div class="header">
        <span>笔试中</span>
        <el-countdown :value="deadline" @finish="autoSubmit" />
      </div>
      <el-scrollbar height="500px">
        <div v-for="(q, i) in questions" :key="q.id" class="question-item">
          <h4>{{ i + 1 }}. [{{ typeLabel(q.type) }}] {{ q.content }}</h4>
          <!-- 根据 type 渲染不同答题组件 -->
        </div>
      </el-scrollbar>
      <el-button type="primary" @click="handleSubmit">提交</el-button>
    </el-card>
  </div>
</template>
```

**Step 3: 创建结果页 WrittenTestResult.vue**

显示：总分、每题对错、正确答案、解析

**Step 4: 手动测试完整流程**

启动后端+前端，创建项目 → 开始笔试 → 答题 → 提交 → 查看结果

**Step 5: Commit**

```bash
git add -A && git commit -m "feat: add written test frontend UI"
```

---

## Phase 7: 面试官风格

### Task 14: 面试官风格 CRUD + 预设数据

**Files:**
- Create: `controller/PersonaController.java`
- Create: `service/PersonaService.java`
- Create: `service/impl/PersonaServiceImpl.java`
- Create: `util/PersonaSeeder.java`

**Step 1: 创建预设面试官风格**

```java
@Component
@RequiredArgsConstructor
public class PersonaSeeder implements CommandLineRunner {
    private final InterviewerPersonaRepository repository;

    @Override
    public void run(String... args) {
        if (repository.findByIsPreset(true).isEmpty()) {
            seedPersona("技术深挖型",
                "不断追问底层原理和实现细节，刨根问底，要求解释到源码级别",
                """{"tone":"serious","speed":"medium","aggressiveness":0.8,"followupStrategy":"deep_dive"}""");
            seedPersona("压力面试型",
                "质疑回答，施加压力，指出不足，考察抗压能力",
                """{"tone":"stern","speed":"fast","aggressiveness":0.9,"followupStrategy":"challenge"}""");
            seedPersona("温和引导型",
                "友好亲切，答不上来会给提示，换角度引导",
                """{"tone":"warm","speed":"slow","aggressiveness":0.3,"followupStrategy":"guide"}""");
            seedPersona("项目实战型",
                "围绕简历项目展开，问架构决策、踩坑经验、技术选型理由",
                """{"tone":"professional","speed":"medium","aggressiveness":0.5,"followupStrategy":"project_drill"}""");
            seedPersona("八股文型",
                "按知识点清单逐个问，标准化考察，不追问太深",
                """{"tone":"neutral","speed":"medium","aggressiveness":0.4,"followupStrategy":"checklist"}""");
        }
    }

    private void seedPersona(String name, String desc, String config) {
        InterviewerPersona p = new InterviewerPersona();
        p.setName(name);
        p.setDescription(desc);
        p.setStyleConfig(config);
        p.setIsPreset(true);
        repository.save(p);
    }
}
```

**Step 2: CRUD controller + service**

标准 CRUD：`GET /persona`, `POST /persona`, `PUT /persona/{id}`, `DELETE /persona/{id}` (preset 不可删)

**Step 3: Commit**

```bash
git add -A && git commit -m "feat: add interviewer persona CRUD and preset data"
```

---

## Phase 8: 模拟面试

### Task 15: 模拟面试会话与对话生成

**Files:**
- Create: `controller/MockInterviewController.java`
- Create: `service/MockInterviewService.java`
- Create: `service/impl/MockInterviewServiceImpl.java`

**Step 1: 创建服务接口**

```java
public interface MockInterviewService {
    // 开始模拟面试：创建 session + 生成大纲
    MockInterviewStartResponse start(Long projectId, Long personaId, boolean referenceWrittenTest);
    // 用户回答 → STT 文本 → AI 生成下一个问题
    MockInterviewMessage respond(Long sessionId, String userText, String audioPath);
    // 面试官主动收尾
    MockInterviewMessage concludeInterview(Long sessionId);
    // 获取对话历史
    List<MockInterviewMessage> getMessages(Long sessionId);
    // 切换面试官
    void switchPersona(Long sessionId, Long newPersonaId);
}
```

**Step 2: 实现对话生成核心逻辑**

```java
@Override
public MockInterviewMessage respond(Long sessionId, String userText, String audioPath) {
    // 1. 保存用户消息
    MockInterviewMessage userMsg = new MockInterviewMessage();
    userMsg.setSessionId(sessionId);
    userMsg.setRole("CANDIDATE");
    userMsg.setContent(userText);
    userMsg.setAudioPath(audioPath);
    messageRepository.save(userMsg);

    // 2. 构建对话上下文（所有历史消息 + 面试官风格 + 大纲 + 简历 + 弱点标签）
    List<MockInterviewMessage> history = messageRepository
            .findBySessionIdOrderByCreatedAt(sessionId);
    InterviewerPersona persona = personaRepository.findById(
            getCurrentPersonaId(sessionId)).orElseThrow();

    // 3. LLM 生成面试官回复
    String systemPrompt = buildInterviewerSystemPrompt(persona, sessionId);
    List<LlmService.Message> messages = buildLlmMessages(systemPrompt, history);

    LlmService.LlmResponse response = llmService.chat(messages);

    // 4. 保存面试官消息
    MockInterviewMessage aiMsg = new MockInterviewMessage();
    aiMsg.setSessionId(sessionId);
    aiMsg.setRole("INTERVIEWER");
    aiMsg.setContent(response.getContent());
    aiMsg.setPersonaId(persona.getId());
    return messageRepository.save(aiMsg);
}
```

**Step 3: 实现 system prompt 构建**

```java
private String buildInterviewerSystemPrompt(InterviewerPersona persona, Long sessionId) {
    return """
        你是一位面试官，风格：%s。
        风格描述：%s
        
        面试大纲：%s
        候选人简历摘要：%s
        候选人弱点标签：%s
        
        规则：
        1. 根据候选人回答动态决定下一步：追问深挖 / 转换话题 / 给予反馈后继续
        2. 如果候选人回答有误，委婉指出
        3. 当大纲所有主题覆盖完毕，自然收尾，给出简短总结
        4. 每次只说一轮（一个问题或一段追问），不要一次问多个问题
        """.formatted(
            persona.getName(), persona.getDescription(),
            getOutline(sessionId), getResumeSummary(sessionId),
            getWeaknessTags(sessionId));
}
```

**Step 4: 创建 controller**

```java
@RestController
@RequestMapping("/mock-interview")
public class MockInterviewController {
    @PostMapping("/start/{projectId}")
    public ApiResponse<MockInterviewStartResponse> start(...) {}
    @PostMapping("/respond/{sessionId}")
    public ApiResponse<MockInterviewMessage> respond(...) {}
    @PostMapping("/conclude/{sessionId}")
    public ApiResponse<MockInterviewMessage> conclude(...) {}
    @GetMapping("/messages/{sessionId}")
    public ApiResponse<List<MockInterviewMessage>> messages(...) {}
    @PutMapping("/switch-persona/{sessionId}")
    public ApiResponse<Void> switchPersona(...) {}
}
```

**Step 5: 测试 — mock LlmService，验证对话流程**

```java
@Test
void respond_savesUserAndAiMessages() {
    // mock messageRepository 和 llmService
    // 验证用户消息和 AI 消息都被保存
}
```

**Step 6: Commit**

```bash
git add -A && git commit -m "feat: add mock interview session and dialogue generation"
```

---

## Phase 9: 语音集成

### Task 16: 腾讯云 STT/TTS 服务

**Files:**
- Create: `config/TencentCloudConfig.java`
- Create: `service/VoiceService.java`
- Create: `service/impl/VoiceServiceImpl.java`

**Step 1: 添加腾讯云 SDK 依赖到 pom.xml**

```xml
<dependency>
    <groupId>com.tencentcloudapi</groupId>
    <artifactId>tencentcloud-sdk-java-asr</artifactId>
    <version>3.3.0</version>
</dependency>
<dependency>
    <groupId>com.tencentcloudapi</groupId>
    <artifactId>tencentcloud-sdk-java-tts</artifactId>
    <version>3.3.0</version>
</dependency>
```

**Step 2: 创建配置类读取腾讯云密钥**

```java
@Configuration
@ConfigurationProperties(prefix = "tencent.cloud")
@Data
public class TencentCloudConfig {
    private String secretId;
    private String secretKey;
    private String appId;
    private String asrRegion = "ap-guangzhou";
    private String ttsRegion = "ap-guangzhou";
}
```

application.yml 添加：
```yaml
tencent:
  cloud:
    secret-id: ${TENCENT_SECRET_ID:}
    secret-key: ${TENCENT_SECRET_KEY:}
    app-id: ${TENCENT_APP_ID:}
```

**Step 3: 创建 VoiceService 接口和实现**

```java
public interface VoiceService {
    String recognizeSpeech(byte[] audioData);  // STT
    byte[] synthesizeSpeech(String text, String voiceType);  // TTS
}
```

VoiceServiceImpl 关键逻辑：
- `recognizeSpeech`: 调用腾讯云 ASR 一句话识别 API，传入音频 byte[]，返回识别文本
- `synthesizeSpeech`: 调用腾讯云 TTS API，传入文本和音色参数（受面试官风格影响），返回音频 byte[]

```java
@Service
@RequiredArgsConstructor
public class VoiceServiceImpl implements VoiceService {
    private final TencentCloudConfig config;

    @Override
    public String recognizeSpeech(byte[] audioData) {
        // 构建 ASR 客户端，调用一句话识别
        // 返回 ResultText
    }

    @Override
    public byte[] synthesizeSpeech(String text, String voiceType) {
        // 构建 TTS 客户端，调用基础语音合成
        // voiceType 映射面试官风格 → 音色参数
        // 返回音频 byte[]
    }
}
```

**Step 4: 在 MockInterviewService 中集成语音**

- `respond` 方法改为接收音频 byte[]：先 STT → 文本 → LLM → TTS → 返回音频
- 或者保持文本接口，语音处理单独一个 controller（前端分别调用 STT 和 TTS）

推荐方案：**前端分别调用**，后端提供独立的 STT/TTS endpoint：

```java
@RestController
@RequestMapping("/voice")
public class VoiceController {
    @PostMapping("/stt")
    public ApiResponse<String> recognize(@RequestParam("file") MultipartFile audio) {
        return ApiResponse.success(voiceService.recognizeSpeech(audio.getBytes()));
    }

    @PostMapping("/tts")
    public ResponseEntity<byte[]> synthesize(@RequestBody TtsRequest req) {
        byte[] audio = voiceService.synthesizeSpeech(req.getText(), req.getVoiceType());
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(audio);
    }
}
```

**Step 5: Commit**

```bash
git add -A && git commit -m "feat: add Tencent Cloud STT/TTS voice service"
```

---

### Task 17: 音频录制与存储

**Files:**
- Modify: `MockInterviewService.respond()` — 保存音频文件
- Create: `util/AudioStorageUtil.java`

**Step 1: 创建音频存储工具**

```java
@Component
public class AudioStorageUtil {
    @Value("${app.audio-dir}")
    private String audioDir;

    public String saveAudio(byte[] data, Long sessionId, String role) {
        String filename = sessionId + "_" + role + "_" + 
                System.currentTimeMillis() + ".wav";
        Path path = Paths.get(audioDir, sessionId.toString());
        Files.createDirectories(path);
        Files.write(path.resolve(filename), data);
        return path.resolve(filename).toString();
    }
}
```

**Step 2: 在 respond 方法中保存用户音频**

```java
// 在 MockInterviewServiceImpl.respond() 中
String audioPath = audioStorageUtil.saveAudio(audioBytes, sessionId, "CANDIDATE");
userMsg.setAudioPath(audioPath);
```

**Step 3: 提供音频回放 endpoint**

```java
@GetMapping("/audio/{sessionId}/{filename}")
public ResponseEntity<Resource> getAudio(@PathVariable Long sessionId,
                                          @PathVariable String filename) {
    // 返回音频文件
}
```

**Step 4: Commit**

```bash
git add -A && git commit -m "feat: add audio recording storage and playback endpoint"
```

---

### Task 18: 模拟面试前端 UI

**Files:**
- Create: `frontend/src/views/mock-interview/MockInterviewChat.vue`
- Create: `frontend/src/api/mock-interview.js`
- Create: `frontend/src/api/voice.js`
- Create: `frontend/src/components/AudioRecorder.vue`

**Step 1: 创建音频录制组件 AudioRecorder.vue**

```vue
<template>
  <div class="audio-recorder">
    <el-button
      :type="recording ? 'danger' : 'primary'"
      @click="recording ? stopRecording() : startRecording()"
      :icon="recording ? Microphone : Microphone"
      circle
    />
    <span v-if="recording" class="recording-indicator">
      录音中... {{ duration }}s
    </span>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { ElMessage } from 'element-plus'

const props = defineProps({
  maxDuration: { type: Number, default: 120 }
})
const emit = defineEmits(['audio-recorded'])

const recording = ref(false)
const duration = ref(0)
let mediaRecorder = null
let audioChunks = []
let timer = null

async function startRecording() {
  const stream = await navigator.mediaDevices.getUserMedia({ audio: true })
  mediaRecorder = new MediaRecorder(stream)
  audioChunks = []
  mediaRecorder.ondataavailable = e => audioChunks.push(e.data)
  mediaRecorder.onstop = () => {
    const blob = new Blob(audioChunks, { type: 'audio/wav' })
    emit('audio-recorded', blob)
    stream.getTracks().forEach(t => t.stop())
  }
  mediaRecorder.start()
  recording.value = true
  duration.value = 0
  timer = setInterval(() => {
    duration.value++
    if (duration.value >= props.maxDuration) stopRecording()
  }, 1000)
}

function stopRecording() {
  if (mediaRecorder && mediaRecorder.state !== 'inactive') {
    mediaRecorder.stop()
  }
  recording.value = false
  clearInterval(timer)
}
</script>
```

**Step 2: 创建 voice API**

```javascript
import request from './request'

export const recognizeSpeech = (audioBlob) => {
  const formData = new FormData()
  formData.append('file', audioBlob, 'audio.wav')
  return request.post('/voice/stt', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

export const synthesizeSpeech = (text, voiceType) =>
  request.post('/voice/tts', { text, voiceType }, {
    responseType: 'blob'
  })
```

**Step 3: 创建聊天界面 MockInterviewChat.vue**

关键功能：
- 消息列表（面试官/用户，区分样式）
- 面试官消息播报 TTS 音频
- 用户录音按钮 → 发送音频 → 后端 STT + LLM + TTS → 显示回复 + 播放音频
- 切换面试官按钮
- 结束面试按钮

```vue
<template>
  <div class="mock-interview-chat">
    <!-- 消息列表 -->
    <div class="messages" ref="messageList">
      <div v-for="msg in messages" :key="msg.id"
           :class="['message', msg.role === 'INTERVIEWER' ? 'interviewer' : 'candidate']">
        <div class="avatar">{{ msg.role === 'INTERVIEWER' ? '面' : '我' }}</div>
        <div class="content">
          <p>{{ msg.content }}</p>
          <el-button v-if="msg.audioPath" link @click="playAudio(msg.audioPath)">
            <el-icon><VideoPlay /></el-icon> 回放
          </el-button>
        </div>
      </div>
    </div>
    <!-- 录音区 -->
    <div class="input-area">
      <AudioRecorder @audio-recorded="handleAudioRecorded" />
      <el-button @click="switchPersona">换面试官</el-button>
      <el-button type="danger" @click="concludeInterview">结束面试</el-button>
    </div>
  </div>
</template>
```

**Step 4: 实现录音→发送→接收流程**

```javascript
async function handleAudioRecorded(audioBlob) {
  loading.value = true
  try {
    // 1. 发送音频到后端 → 后端 STT + LLM + TTS → 返回面试官回复 + 音频
    const formData = new FormData()
    formData.append('audio', audioBlob, 'audio.wav')
    const res = await request.post(
      `/mock-interview/respond/${sessionId}`, formData)
    // 2. 添加消息到列表
    messages.value.push(res.data.userMessage)
    messages.value.push(res.data.aiMessage)
    // 3. 播放面试官音频
    if (res.data.audioBase64) {
      playAudioBase64(res.data.audioBase64)
    }
  } finally {
    loading.value = false
  }
}
```

**Step 5: Commit**

```bash
git add -A && git commit -m "feat: add mock interview chat UI with voice recording"
```

---

## Phase 10: 面试分析

### Task 19: 面试分析服务

**Files:**
- Create: `service/AnalysisService.java`
- Create: `service/impl/AnalysisServiceImpl.java`
- Create: `controller/AnalysisController.java`

**Step 1: 创建分析服务接口**

```java
public interface AnalysisService {
    SessionAnalysis analyzeSession(Long sessionId);
    List<QuestionAnalysis> analyzeQuestions(Long sessionId);
    HistoricalAnalysis analyzeHistory(Long projectId);
    void updateWeaknessTags(Long projectId, Long sessionId);
}
```

**Step 2: 实现笔试分析**

```java
@Override
public SessionAnalysis analyzeSession(Long sessionId) {
    InterviewSession session = sessionRepository.findById(sessionId).orElseThrow();
    
    if ("WRITTEN".equals(session.getType())) {
        return analyzeWrittenSession(session);
    } else {
        return analyzeMockSession(session);
    }
}

private SessionAnalysis analyzeWrittenSession(InterviewSession session) {
    List<WrittenTestQuestion> questions = questionRepository
            .findBySessionIdOrderByOrderNum(session.getId());
    List<WrittenTestAnswer> answers = answerRepository.findBySessionId(session.getId());
    
    // 构建题目+答案文本，让 LLM 分析
    String systemPrompt = """
        分析以下笔试结果，返回 JSON：
        {"overallScore": 0-100, "strengths": [...], "weaknesses": [...],
         "knowledgeGaps": [...], "improvementPlan": {"topics": [...], "suggestions": [...]}}
        """;
    String userPrompt = buildWrittenTestAnalysisContext(questions, answers);
    
    LlmService.LlmResponse response = llmService.chat(systemPrompt, userPrompt);
    // 解析 JSON → 构建 SessionAnalysis 实体
}
```

**Step 3: 实现模拟面试分析**

```java
private SessionAnalysis analyzeMockSession(InterviewSession session) {
    List<MockInterviewMessage> messages = messageRepository
            .findBySessionIdOrderByCreatedAt(session.getId());
    
    String systemPrompt = """
        分析以下面试对话记录，返回 JSON：
        {"overallScore": 0-100, "strengths": [...], "weaknesses": [...],
         "knowledgeGaps": [...], "communicationEvaluation": "...",
         "improvementPlan": {"topics": [...], "suggestions": [...]},
         "questionAnalysis": [
           {"questionContent": "...", "answerContent": "...",
            "accuracy": 0-10, "depth": 0-10, "clarity": 0-10,
            "fluency": 0-10, "tone": "...", "improvementSuggestion": "..."}
         ]}
        评估维度：准确度、深度、结构清晰度、流畅度、语气。
        流畅度考虑：停顿、语气词频率、重复。
        """;
    String userPrompt = buildMockInterviewAnalysisContext(messages);
    
    LlmService.LlmResponse response = llmService.chat(systemPrompt, userPrompt);
    // 解析 JSON → 构建 SessionAnalysis + QuestionAnalysis
}
```

**Step 4: 实现弱点标签更新**

```java
@Override
public void updateWeaknessTags(Long projectId, Long sessionId) {
    SessionAnalysis analysis = sessionAnalysisRepository.findBySessionId(sessionId);
    // 解析 knowledgeGaps JSON
    // 对每个 gap，查找或创建 WeaknessTag
    // 更新 masteryLevel (WEAK) + occurrenceCount++
}
```

**Step 5: 实现历史综合分析**

```java
@Override
public HistoricalAnalysis analyzeHistory(Long projectId) {
    List<SessionAnalysis> allAnalyses = getAllSessionAnalyses(projectId);
    String systemPrompt = """
        基于以下多次面试分析结果，生成历史综合分析 JSON：
        {"trends": [{"dimension": "...", "trend": "up/down/stable", "detail": "..."}],
         "recurringWeaknesses": [...],
         "recommendedFocus": [...],
         "overallProgress": "..."}
        """;
    String userPrompt = buildHistoryContext(allAnalyses);
    LlmService.LlmResponse response = llmService.chat(systemPrompt, userPrompt);
    // 保存 HistoricalAnalysis
}
```

**Step 6: 创建 controller**

```java
@RestController
@RequestMapping("/analysis")
public class AnalysisController {
    @GetMapping("/session/{sessionId}")
    public ApiResponse<SessionAnalysis> analyzeSession(...) {}
    @GetMapping("/history/{projectId}")
    public ApiResponse<HistoricalAnalysis> analyzeHistory(...) {}
}
```

**Step 7: 在面试提交时触发分析**

笔试 submit 和模拟面试 conclude 时，自动调用 `analyzeSession` + `updateWeaknessTags`。

**Step 8: 测试 → Commit**

```bash
cd backend && mvn test -Dtest=AnalysisService*
git add -A && git commit -m "feat: add interview analysis service (session, question, historical, weakness tags)"
```

---

### Task 20: 分析报告前端 UI

**Files:**
- Create: `frontend/src/views/analysis/SessionReport.vue`
- Create: `frontend/src/views/analysis/HistoryReport.vue`
- Create: `frontend/src/api/analysis.js`

**Step 1: 创建分析 API**

```javascript
export const getSessionAnalysis = (sessionId) => request.get(`/analysis/session/${sessionId}`)
export const getHistoryAnalysis = (projectId) => request.get(`/analysis/history/${projectId}`)
```

**Step 2: 创建单次分析报告 SessionReport.vue**

展示：
- 总体评分（仪表盘）
- 优势项 / 弱项项（标签）
- 知识盲区（列表）
- 沟通表达能力（仅模拟面试，文字段落）
- 改进方案（知识点列表 + 建议）
- 单题分析（折叠面板，每题展示各维度评分 + 改进建议）

**Step 3: 创建历史综合分析 HistoryReport.vue**

展示：
- 能力趋势（折线图：多次面试评分变化）
- 反复出错知识点（标签云或列表）
- 推荐重点练习方向
- 整体进步评价

**Step 4: Commit**

```bash
git add -A && git commit -m "feat: add analysis report frontend UI"
```

---

## Phase 11: 前端整合与路由

### Task 21: 路由与布局

**Files:**
- Create: `frontend/src/router/index.js`
- Create: `frontend/src/views/Layout.vue`
- Create: `frontend/src/views/Home.vue`

**Step 1: 创建路由**

```javascript
import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  {
    path: '/',
    component: () => import('@/views/Layout.vue'),
    children: [
      { path: '', name: 'home', component: () => import('@/views/Home.vue') },
      { path: 'project/:id', name: 'project-detail', component: () => import('@/views/ProjectDetail.vue') },
      { path: 'written-test/:sessionId', name: 'written-test', component: () => import('@/views/written-test/WrittenTestExam.vue') },
      { path: 'mock-interview/:sessionId', name: 'mock-interview', component: () => import('@/views/mock-interview/MockInterviewChat.vue') },
      { path: 'analysis/session/:sessionId', name: 'session-report', component: () => import('@/views/analysis/SessionReport.vue') },
      { path: 'analysis/history/:projectId', name: 'history-report', component: () => import('@/views/analysis/HistoryReport.vue') },
      { path: 'settings', name: 'settings', component: () => import('@/views/Settings.vue') },
    ]
  }
]

export default createRouter({ history: createWebHistory(), routes })
```

**Step 2: 创建 Layout.vue — 侧边栏导航**

```vue
<template>
  <el-container>
    <el-aside width="200px">
      <el-menu :default-active="activeMenu" router>
        <el-menu-item index="/">首页</el-menu-item>
        <el-menu-item index="/settings">设置</el-menu-item>
      </el-menu>
    </el-aside>
    <el-main>
      <router-view />
    </el-main>
  </el-container>
</template>
```

**Step 3: 创建 Home.vue — 项目列表**

展示所有面试项目卡片，每个项目显示：名称、职位、面试次数、上次面试时间、最近评分。支持创建新项目。

**Step 4: 创建 Settings.vue — AI 模型 & 腾讯云配置**

表单配置：LLM provider、API URL、API Key、Model Name。腾讯云 SecretId/SecretKey/AppId。

**Step 5: Commit**

```bash
git add -A && git commit -m "feat: add frontend routing, layout, home page, and settings"
```

---

### Task 22: 项目详情页

**Files:**
- Create: `frontend/src/views/ProjectDetail.vue`

**Step 1: 创建项目详情页**

展示内容：
- 项目信息（名称、描述）
- 简历上传/查看分析
- 职位需求（查看/编辑/从简历生成）
- 面试历史列表（每次面试的类型、时间、状态、评分、查看报告按钮）
- 开始新面试按钮（选择笔试/模拟面试/完整流程）
- 弱点标签展示
- 历史综合分析入口

**Step 2: Commit**

```bash
git add -A && git commit -m "feat: add project detail page with resume, position, and session history"
```

---

## Phase 12: 收尾

### Task 23: 数据初始化与种子数据

**Files:**
- Create: `backend/src/main/resources/data.sql`

**Step 1: 添加默认 AI 模型配置种子数据**

```sql
INSERT INTO ai_model_config (provider, api_url, api_key, model_name, is_active, created_at)
SELECT 'deepseek', 'https://api.deepseek.com/v1/chat/completions', '', 'deepseek-chat', true, datetime('now')
WHERE NOT EXISTS (SELECT 1 FROM ai_model_config);
```

**Step 2: 配置 Spring Boot 启动时执行**

application.yml 添加：
```yaml
spring:
  sql:
    init:
      mode: always
      continue-on-error: true
```

**Step 3: Commit**

```bash
git add -A && git commit -m "feat: add seed data for AI model config"
```

---

### Task 24: 端到端验证

**Step 1: 启动后端**

```bash
cd backend && mvn spring-boot:run
```
Expected: Spring Boot 启动在 8080 端口

**Step 2: 启动前端**

```bash
cd frontend && npm run dev
```
Expected: Vite 启动在 5173 端口

**Step 3: 完整流程验证**

1. 打开 http://localhost:5173
2. Settings 页配置 DeepSeek API Key
3. 创建面试项目
4. 上传简历 PDF → 分析 → 确认职位
5. 开始笔试 → 答题 → 提交 → 查看分析
6. 开始模拟面试 → 录音回答 → 收到面试官回复（文字+语音）→ 多轮对话 → 结束 → 查看分析
7. 再次面试 → 验证自适应出题（优先弱点知识点）
8. 查看历史综合分析

**Step 4: 修复发现的问题**

**Step 5: Commit**

```bash
git add -A && git commit -m "chore: end-to-end verification and fixes"
```

---

## 开放问题（实现阶段细化）

1. **预设面试官风格具体参数** — styleConfig JSON 的 tone/speed/aggressiveness 值如何映射到腾讯云 TTS 参数
2. **弱点标签数据结构** — masteryLevel 枚举值的具体阈值定义
3. **笔试题量** — 默认 10-15 题，可配置
4. **LLM JSON 解析容错** — LLM 返回的 JSON 可能不规范，需加容错解析
5. **录音格式转换** — 浏览器 MediaRecorder 输出 webm，腾讯云 ASR 可能需要 wav/pcm，需做格式转换
6. **面试官 TTS 风格映射** — 面试官 styleConfig 如何映射到 TTS 音色参数（语速、音量、音色 ID）
