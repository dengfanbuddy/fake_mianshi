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

    private final AIModelConfigRepository configRepository;
    private final HttpClient httpClient;

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
        List<Message> messages = new ArrayList<>();
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            messages.add(new Message("system", systemPrompt));
        }
        messages.add(new Message("user", userPrompt));
        return chat(messages);
    }

    @Override
    public LlmResponse chat(List<Message> messages) {
        AIModelConfig config = configRepository.findByIsActiveTrue()
                .orElseThrow(() -> new BusinessException("未配置可用的AI模型，请在设置中配置"));

        String requestJson;
        try {
            requestJson = buildRequestBody(config, messages);
        } catch (JacksonException e) {
            throw new BusinessException("AI模型请求体构建失败: " + e.getMessage(), e);
        }

        String responseBody = doPost(config, requestJson);
        return parseResponse(responseBody);
    }

    /**
     * 构建 OpenAI 兼容的 chat/completions 请求体（model, messages, temperature, max_tokens, stream）。
     */
    private String buildRequestBody(AIModelConfig config, List<Message> messages) throws JacksonException {
        ObjectNode root = OBJECT_MAPPER.createObjectNode();
        root.put("model", config.getModelName());
        ArrayNode messagesNode = root.putArray("messages");
        for (Message m : messages) {
            ObjectNode msg = messagesNode.addObject();
            msg.put("role", m.role());
            msg.put("content", m.content());
        }
        root.put("temperature", 0.7);
        root.put("max_tokens", 4096);
        root.put("stream", false);
        return OBJECT_MAPPER.writeValueAsString(root);
    }

    /**
     * 发起 POST 请求并返回响应体字符串；非 2xx 状态码时抛出携带服务端错误信息的 BusinessException。
     */
    private String doPost(AIModelConfig config, String requestJson) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(config.getApiUrl()))
                .header("Authorization", "Bearer " + config.getApiKey())
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(60))
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
