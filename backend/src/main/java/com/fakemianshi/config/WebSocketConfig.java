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
 * <p>该端点暴露为 /api/ws/interview（前端语音流式客户端连接到该地址）。
 */
@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {

    private final InterviewVoiceWebSocketHandler interviewVoiceWebSocketHandler;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(interviewVoiceWebSocketHandler, "/api/ws/interview")
                // 仅允许本机来源，防止跨站 WebSocket 劫持（CSWSH）驱动面试/消耗额度
                .setAllowedOriginPatterns("http://localhost:*", "http://127.0.0.1:*");
    }
}
