package com.fakemianshi.service.impl;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import com.fakemianshi.config.BusinessException;
import com.fakemianshi.dto.LlmResponse;
import com.fakemianshi.entity.AIModelConfig;
import com.fakemianshi.repository.AIModelConfigRepository;
import com.fakemianshi.service.LlmService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * LLM 服务默认实现。
 *
 * <p>使用 Java 17 自带 {@link HttpClient} 发起 OpenAI 兼容的 chat/completions 请求，
 * 无需引入额外 HTTP 依赖。
 */
@Service
public class LlmServiceImpl implements LlmService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /** 默认输出 token 上限（普通对话/开场白等短输出） */
    public static final int DEFAULT_MAX_TOKENS = 4096;

    /**
     * 长文本任务（笔试出题、分析报告）输出 token 上限。
     * DeepSeek 模型输出能力最大 384K，64K 对出题/分析报告绰绰有余且不接近 API 限制。
     */
    public static final int LONG_TASK_MAX_TOKENS = 65536;

    private final AIModelConfigRepository configRepository;
    private final HttpClient httpClient;

    /** 统一追加的系统指令：要求模型直接输出结果，减少/禁用思考过程（配合请求参数 thinking:disabled 双保险） */
    private static final String DIRECT_OUTPUT_HINT =
            "（系统要求：请直接输出最终结果，不要输出任何思考过程、推理过程或额外说明。）";

    /** 构造 system+user 消息，并在 system 末尾追加"直接输出"指令 */
    private List<Message> buildMessages(String systemPrompt, String userPrompt) {
        List<Message> messages = new ArrayList<>();
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            messages.add(new Message("system", systemPrompt + DIRECT_OUTPUT_HINT));
        } else {
            messages.add(new Message("system", DIRECT_OUTPUT_HINT.trim()));
        }
        messages.add(new Message("user", userPrompt));
        return messages;
    }

    /**
     * 生产环境构造器：创建带超时配置的默认 HttpClient。
     */
    @Autowired
    public LlmServiceImpl(AIModelConfigRepository configRepository) {
        this(configRepository, HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build());
    }

    /**
     * 测试用构造器：可注入 mock 的 HttpClient，避免真实网络请求。
     */
    public LlmServiceImpl(AIModelConfigRepository configRepository, HttpClient httpClient) {
        this.configRepository = configRepository;
        this.httpClient = httpClient;
    }

    @Override
    public LlmResponse chat(String systemPrompt, String userPrompt) {
        return chat(buildMessages(systemPrompt, userPrompt));
    }

    @Override
    public LlmResponse chat(String systemPrompt, String userPrompt, int maxTokens) {
        return chat(buildMessages(systemPrompt, userPrompt), maxTokens);
    }

    @Override
    public LlmResponse chat(List<Message> messages) {
        return chat(messages, DEFAULT_MAX_TOKENS);
    }

    @Override
    public LlmResponse chat(List<Message> messages, int maxTokens) {
        AIModelConfig config = configRepository.findByIsActiveTrue()
                .orElseThrow(() -> new BusinessException("未配置可用的AI模型，请在设置中配置"));

        String requestJson;
        try {
            requestJson = buildRequestBody(config, messages, maxTokens, false);
        } catch (JacksonException e) {
            throw new BusinessException("AI模型请求体构建失败: " + e.getMessage(), e);
        }

        String responseBody = doPost(config, requestJson);
        return parseResponse(responseBody);
    }

    @Override
    public String chatStream(String systemPrompt, String userPrompt, int maxTokens,
                             java.util.function.Consumer<String> onDelta,
                             java.util.function.Consumer<String> onReasoning) {
        AIModelConfig config = configRepository.findByIsActiveTrue()
                .orElseThrow(() -> new BusinessException("未配置可用的AI模型，请在设置中配置"));

        List<Message> messages = buildMessages(systemPrompt, userPrompt);

        String requestJson;
        try {
            requestJson = buildRequestBody(config, messages, maxTokens, true);
        } catch (JacksonException e) {
            throw new BusinessException("AI模型请求体构建失败: " + e.getMessage(), e);
        }
        return doPostStream(config, requestJson, onDelta, onReasoning);
    }

    /**
     * 构建 OpenAI 兼容的 chat/completions 请求体（model, messages, temperature, max_tokens, stream）。
     */
    private String buildRequestBody(AIModelConfig config, List<Message> messages, int maxTokens, boolean stream)
            throws JacksonException {
        ObjectNode root = OBJECT_MAPPER.createObjectNode();
        root.put("model", config.getModelName());
        ArrayNode messagesNode = root.putArray("messages");
        for (Message m : messages) {
            ObjectNode msg = messagesNode.addObject();
            msg.put("role", m.role());
            msg.put("content", m.content());
        }
        root.put("temperature", 0.7);
        root.put("max_tokens", maxTokens > 0 ? maxTokens : DEFAULT_MAX_TOKENS);
        root.put("stream", stream);
        // 思考型模型（如 deepseek-v4-flash）禁用思考，直接输出内容，显著提速
        root.putObject("thinking").put("type", "disabled");
        return OBJECT_MAPPER.writeValueAsString(root);
    }

    /**
     * 流式 POST：逐行解析 SSE（data: {...}），提取 choices[0].delta.content 增量回调；
     * 思考型模型输出 reasoning_content 时经 onReasoning 回调（保证前端有实时反馈）。返回完整拼接内容。
     */
    private String doPostStream(AIModelConfig config, String requestJson,
                                java.util.function.Consumer<String> onDelta,
                                java.util.function.Consumer<String> onReasoning) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(config.getApiUrl()))
                .header("Authorization", "Bearer " + config.getApiKey())
                .header("Content-Type", "application/json")
                // 流式长任务（出题），超时放宽
                .timeout(Duration.ofSeconds(180))
                .POST(HttpRequest.BodyPublishers.ofString(requestJson, StandardCharsets.UTF_8))
                .build();

        HttpResponse<java.io.InputStream> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new BusinessException("AI模型调用失败: " + e.getMessage(), e);
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            String errBody = readErrorBody(response);
            throw new BusinessException("AI模型调用失败，状态码: " + response.statusCode() + "，响应: " + errBody);
        }

        StringBuilder full = new StringBuilder();
        try (java.io.BufferedReader reader = new java.io.BufferedReader(
                new java.io.InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.startsWith("data:")) {
                    continue;
                }
                String data = line.substring(5).trim();
                if (data.isEmpty() || "[DONE]".equals(data)) {
                    continue;
                }
                try {
                    JsonNode node = OBJECT_MAPPER.readTree(data);
                    JsonNode delta = node.path("choices").path(0).path("delta");
                    JsonNode content = delta.path("content");
                    if (content.isTextual() && !content.asText().isEmpty()) {
                        String deltaText = content.asText();
                        full.append(deltaText);
                        if (onDelta != null) {
                            onDelta.accept(deltaText);
                        }
                    } else {
                        // 思考型模型：content 为空时推送 reasoning_content，保证前端有实时反馈
                        JsonNode reasoning = delta.path("reasoning_content");
                        if (onReasoning != null && reasoning.isTextual() && !reasoning.asText().isEmpty()) {
                            onReasoning.accept(reasoning.asText());
                        }
                    }
                } catch (JacksonException ignored) {
                    // 跳过无法解析的行（如 keep-alive 注释）
                }
            }
        } catch (IOException e) {
            throw new BusinessException("AI流式响应读取失败: " + e.getMessage(), e);
        }
        return full.toString();
    }

    private String readErrorBody(HttpResponse<java.io.InputStream> response) {
        try (java.io.InputStream in = response.body()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "";
        }
    }

    /**
     * 发起 POST 请求并返回响应体字符串；非 2xx 状态码时抛出携带服务端错误信息的 BusinessException。
     */
    private String doPost(AIModelConfig config, String requestJson) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(config.getApiUrl()))
                .header("Authorization", "Bearer " + config.getApiKey())
                .header("Content-Type", "application/json")
                // 长任务（出题/分析报告）输出可达 60s+，超时放宽到 120s
                .timeout(Duration.ofSeconds(120))
                .POST(HttpRequest.BodyPublishers.ofString(requestJson, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new BusinessException("AI模型调用失败: " + e.getMessage(), e);
        }

        int statusCode = response.statusCode();
        if (statusCode < 200 || statusCode >= 300) {
            throw new BusinessException("AI模型调用失败，状态码: " + statusCode + "，响应: " + response.body());
        }
        return response.body();
    }

    /**
     * 解析 OpenAI 兼容响应：提取 choices[0].message.content、finish_reason 和 usage.total_tokens。
     */
    private LlmResponse parseResponse(String responseBody) {
        try {
            JsonNode root = OBJECT_MAPPER.readTree(responseBody);
            JsonNode choice = root.path("choices").path(0);
            String content = choice.path("message").path("content").asText("");
            String finishReason = choice.path("finish_reason").isMissingNode() ? null : choice.path("finish_reason").asText();
            int totalTokens = root.path("usage").path("total_tokens").asInt(0);
            return new LlmResponse(content, finishReason, totalTokens);
        } catch (JacksonException e) {
            throw new BusinessException("AI模型响应解析失败: " + e.getMessage(), e);
        }
    }
}
