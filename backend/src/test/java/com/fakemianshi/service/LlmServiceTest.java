package com.fakemianshi.service;

import com.fakemianshi.config.BusinessException;
import com.fakemianshi.dto.LlmResponse;
import com.fakemianshi.entity.AIModelConfig;
import com.fakemianshi.repository.AIModelConfigRepository;
import com.fakemianshi.service.impl.LlmServiceImpl;
import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * LLM 服务单元测试：不依赖真实网络，mock HttpClient。
 */
class LlmServiceTest {

    @Test
    void chat_shouldThrowBusinessException_whenNoActiveConfig() {
        AIModelConfigRepository repository = mock(AIModelConfigRepository.class);
        when(repository.findByIsActiveTrue()).thenReturn(Optional.empty());

        LlmService llmService = new LlmServiceImpl(repository);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> llmService.chat("system", "hello"));
        assertEquals("未配置可用的AI模型，请在设置中配置", ex.getMessage());
    }

    @Test
    @SuppressWarnings("unchecked")
    void chat_shouldReturnParsedResponse_whenActiveConfigExists() throws Exception {
        AIModelConfigRepository repository = mock(AIModelConfigRepository.class);
        AIModelConfig config = new AIModelConfig();
        config.setProvider("test-provider");
        config.setApiUrl("http://localhost:9999/v1/chat/completions");
        config.setApiKey("test-key");
        config.setModelName("test-model");
        config.setIsActive(true);
        when(repository.findByIsActiveTrue()).thenReturn(Optional.of(config));

        HttpClient httpClient = mock(HttpClient.class);
        HttpResponse<String> response = mock(HttpResponse.class);
        doReturn(response).when(httpClient).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("""
                {
                  "choices": [{"message": {"content": "你好，我是面试官"}, "finish_reason": "stop"}],
                  "usage": {"total_tokens": 42}
                }
                """);

        LlmService llmService = new LlmServiceImpl(repository, httpClient);
        LlmResponse result = llmService.chat("你是面试官", "你好");

        assertNotNull(result);
        assertEquals("你好，我是面试官", result.getContent());
        assertEquals("stop", result.getFinishReason());
        assertEquals(42, result.getTotalTokens());
    }

    @Test
    @SuppressWarnings("unchecked")
    void chat_shouldThrowBusinessException_whenApiReturnsError() throws Exception {
        AIModelConfigRepository repository = mock(AIModelConfigRepository.class);
        AIModelConfig config = new AIModelConfig();
        config.setProvider("test-provider");
        config.setApiUrl("http://localhost:9999/v1/chat/completions");
        config.setApiKey("test-key");
        config.setModelName("test-model");
        config.setIsActive(true);
        when(repository.findByIsActiveTrue()).thenReturn(Optional.of(config));

        HttpClient httpClient = mock(HttpClient.class);
        HttpResponse<String> response = mock(HttpResponse.class);
        doReturn(response).when(httpClient).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
        when(response.statusCode()).thenReturn(401);
        when(response.body()).thenReturn("{\"error\":{\"message\":\"Invalid API key\"}}");

        LlmService llmService = new LlmServiceImpl(repository, httpClient);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> llmService.chat("system", "hello"));
        assertEquals("AI模型调用失败，状态码: 401，响应: {\"error\":{\"message\":\"Invalid API key\"}}", ex.getMessage());
    }

    @Test
    @SuppressWarnings("unchecked")
    void chat_shouldSupportMessagesList() throws Exception {
        AIModelConfigRepository repository = mock(AIModelConfigRepository.class);
        AIModelConfig config = new AIModelConfig();
        config.setApiUrl("http://localhost:9999/v1/chat/completions");
        config.setApiKey("test-key");
        config.setModelName("test-model");
        when(repository.findByIsActiveTrue()).thenReturn(Optional.of(config));

        HttpClient httpClient = mock(HttpClient.class);
        HttpResponse<String> response = mock(HttpResponse.class);
        doReturn(response).when(httpClient).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("""
                {
                  "choices": [{"message": {"content": "ok"}, "finish_reason": "stop"}],
                  "usage": {"total_tokens": 10}
                }
                """);

        LlmService llmService = new LlmServiceImpl(repository, httpClient);
        LlmResponse result = llmService.chat(List.of(
                new LlmService.Message("system", "sys"),
                new LlmService.Message("assistant", "prev"),
                new LlmService.Message("user", "next")
        ));

        assertNotNull(result);
        assertEquals("ok", result.getContent());
    }
}
