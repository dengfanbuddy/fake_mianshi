package com.fakemianshi.config;

import com.fakemianshi.voice.InterviewVoiceWebSocketHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * WebSocket 配置：注册面试语音流式端点。
 *
 * <p>注意：服务器 context-path 为 /api，因此该端点实际暴露为 /api/ws/interview。
 */
@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {

    private final InterviewVoiceWebSocketHandler interviewVoiceWebSocketHandler;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(interviewVoiceWebSocketHandler, "/ws/interview")
                .setAllowedOriginPatterns("*");
    }
}
