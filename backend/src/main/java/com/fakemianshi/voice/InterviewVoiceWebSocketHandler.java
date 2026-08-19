package com.fakemianshi.voice;

import com.fakemianshi.dto.MockInterviewRespondRequest;
import com.fakemianshi.dto.MockInterviewRespondResponse;
import com.fakemianshi.entity.VoiceConfig;
import com.fakemianshi.service.MockInterviewService;
import com.fakemianshi.service.StreamEventSink;
import com.fakemianshi.service.VoiceConfigService;
import com.fakemianshi.util.AudioStorageUtil;
import com.fakemianshi.util.WavAudio;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import jakarta.annotation.PreDestroy;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.io.ByteArrayOutputStream;
import java.net.http.HttpClient;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 面试语音流式 WebSocket 端点（Phase B）。
 *
 * <p>浏览器 ↔ 后端协议：
 * <ul>
 *   <li>浏览器 → 后端：文本 {@code {"type":"start","sessionId":N}} 开始、
 *       二进制 PCM 分片（16k/16bit/单声道）、文本 {@code {"type":"end"}} 松开发送；</li>
 *   <li>后端 → 浏览器：文本 JSON {@code {type,data}}，type ∈ ready / partial / text / done / error；
 *       二进制帧为面试官语音 PCM 分片（前端排队播放）。</li>
 * </ul>
 * 任一步失败（未配置密钥 / 连接失败 / 识别失败）都发送 error 事件，前端据此降级到非流式路径。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InterviewVoiceWebSocketHandler extends TextWebSocketHandler {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final MockInterviewService mockInterviewService;
    private final VoiceConfigService voiceConfigService;
    private final AudioStorageUtil audioStorageUtil;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private final ExecutorService replyExecutor = Executors.newCachedThreadPool();

    /** 每个浏览器 WebSocket 会话的流式状态 */
    private static final class SessionState {
        Long sessionId;
        TencentAsrSession asrSession;
        final ByteArrayOutputStream pcmBuffer = new ByteArrayOutputStream();
        volatile String candidateAudioPath;
        volatile boolean replyStarted;
        final Object lock = new Object();
    }

    private final Map<String, SessionState> states = new ConcurrentHashMap<>();

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        SessionState state = states.computeIfAbsent(session.getId(), id -> new SessionState());
        try {
            JsonNode node = MAPPER.readTree(message.getPayload());
            String type = node.path("type").asText("");
            switch (type) {
                case "start" -> handleStart(session, state, node);
                case "end" -> handleEnd(session, state);
                default -> log.warn("未知 WS 消息类型: {}", type);
            }
        } catch (Exception e) {
            sendError(session, "消息处理失败: " + e.getMessage());
        }
    }

    @Override
    protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) {
        SessionState state = states.get(session.getId());
        if (state == null) {
            return;
        }
        ByteBuffer buf = message.getPayload();
        byte[] bytes = new byte[buf.remaining()];
        buf.get(bytes);
        synchronized (state.lock) {
            state.pcmBuffer.writeBytes(bytes);
            if (state.asrSession != null) {
                state.asrSession.sendAudio(bytes);
            }
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        SessionState state = states.remove(session.getId());
        if (state != null && state.asrSession != null) {
            state.asrSession.close();
        }
    }

    private void handleStart(WebSocketSession session, SessionState state, JsonNode node) {
        long sessionId = node.path("sessionId").asLong(0);
        if (sessionId <= 0) {
            sendError(session, "缺少 sessionId");
            return;
        }
        state.sessionId = sessionId;
        // 重置轮次状态（支持同连接多轮或新连接）
        state.replyStarted = false;
        state.candidateAudioPath = null;
        state.pcmBuffer.reset();

        VoiceConfig cfg = voiceConfigService.resolveActive();
        if (cfg == null || !cfg.isConfigured()) {
            sendError(session, "未配置腾讯云 SecretId/SecretKey，请在设置页填写并保存");
            return;
        }
        if (cfg.getAppId() == null || cfg.getAppId().isBlank()) {
            sendError(session, "未配置腾讯云 AppId（实时语音识别需要），请在设置页填写或设置环境变量 TENCENT_APP_ID");
            return;
        }
        try {
            state.asrSession = TencentAsrSession.open(
                    cfg.getSecretId(), cfg.getSecretKey(), cfg.getAppId(),
                    new TencentAsrSession.Listener() {
                        @Override
                        public void onPartialResult(String text) {
                            sendJson(session, "partial", Map.of("text", text == null ? "" : text));
                        }

                        @Override
                        public void onFinalResult(String text) {
                            startReply(session, state, text);
                        }

                        @Override
                        public void onError(String message) {
                            sendError(session, message);
                        }
                    }, httpClient);
            sendJson(session, "ready", Map.of());
        } catch (Exception e) {
            sendError(session, "实时语音识别连接失败: " + e.getMessage());
        }
    }

    private void handleEnd(WebSocketSession session, SessionState state) {
        synchronized (state.lock) {
            if (state.asrSession != null) {
                state.asrSession.stop();
            }
            // 保存候选人录音（PCM → WAV）供回放复盘
            if (state.pcmBuffer.size() > 0) {
                byte[] wav = WavAudio.pcmToWav(state.pcmBuffer.toByteArray(),
                        WavAudio.SAMPLE_RATE, WavAudio.CHANNELS, WavAudio.BITS_PER_SAMPLE);
                try {
                    String abs = audioStorageUtil.saveAudio(wav, state.sessionId, "candidate");
                    state.candidateAudioPath = audioStorageUtil.toPlayablePath(abs);
                } catch (Exception e) {
                    log.warn("候选人录音保存失败: {}", e.getMessage());
                }
            }
        }
    }

    /** ASR 最终结果就绪后，在后台线程跑全链路流式回复 */
    private void startReply(WebSocketSession session, SessionState state, String text) {
        synchronized (state.lock) {
            if (state.replyStarted) {
                return;
            }
            state.replyStarted = true;
        }
        final Long sessionId = state.sessionId;
        final String candidateText = text == null ? "" : text.trim();
        final String audioPath = state.candidateAudioPath;
        if (sessionId == null) {
            sendError(session, "缺少会话信息");
            return;
        }
        if (candidateText.isEmpty()) {
            sendJson(session, "stt_empty", Map.of("message", "未能识别到内容"));
            return;
        }
        // 立即把识别文本推给前端展示（不等待 LLM 回复）
        sendJson(session, "user_text", Map.of("text", candidateText, "audioPath", audioPath == null ? "" : audioPath));

        replyExecutor.execute(() -> {
            MockInterviewRespondRequest req = new MockInterviewRespondRequest();
            req.setUserText(candidateText);
            req.setAudioPath(audioPath);
            mockInterviewService.streamInterviewReply(sessionId, req, new StreamEventSink() {
                @Override
                public void onTextDelta(String delta) {
                    sendJson(session, "text", Map.of("delta", delta == null ? "" : delta));
                }

                @Override
                public void onAudio(byte[] pcmChunk) {
                    sendBinary(session, pcmChunk);
                }

                @Override
                public void onDone(MockInterviewRespondResponse response) {
                    sendJson(session, "done", response);
                }

                @Override
                public void onError(String message) {
                    sendError(session, message);
                }
            });
        });
    }

    private void sendJson(WebSocketSession session, String type, Object data) {
        try {
            ObjectNode envelope = MAPPER.createObjectNode();
            envelope.put("type", type);
            envelope.set("data", MAPPER.readTree(MAPPER.writeValueAsString(data)));
            sendText(session, MAPPER.writeValueAsString(envelope));
        } catch (Exception e) {
            log.warn("WS 发送失败: {}", e.getMessage());
        }
    }

    private void sendError(WebSocketSession session, String message) {
        sendJson(session, "error", Map.of("message", message));
    }

    private void sendBinary(WebSocketSession session, byte[] pcm) {
        if (pcm == null || pcm.length == 0) {
            return;
        }
        try {
            session.sendMessage(new BinaryMessage(pcm));
        } catch (Exception e) {
            log.warn("WS 音频发送失败: {}", e.getMessage());
        }
    }

    private void sendText(WebSocketSession session, String text) {
        try {
            synchronized (session) {
                session.sendMessage(new TextMessage(text));
            }
        } catch (Exception e) {
            log.warn("WS 发送失败: {}", e.getMessage());
        }
    }

    @PreDestroy
    public void shutdown() {
        replyExecutor.shutdownNow();
    }
}
