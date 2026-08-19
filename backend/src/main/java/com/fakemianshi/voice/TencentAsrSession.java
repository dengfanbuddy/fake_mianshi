package com.fakemianshi.voice;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 腾讯云实时语音识别（ASR v2，WebSocket）客户端会话。
 *
 * <p>用法：{@link #open} 建立连接 → {@link #sendAudio} 持续推送 16k/16bit/单声道 PCM →
 * {@link #stop} 发送结束标记 → 服务端返回 final 结果后回调 {@link Listener#onFinalResult}。
 * 识别过程中的中间结果回调 {@link Listener#onPartialResult}。
 *
 * <p>基于 JDK 自带 {@link java.net.http.WebSocket} 实现，不引入第三方 WS 客户端依赖。
 * 签名算法与官方 SDK 一致：{@code signature = base64(HmacSHA1(secretKey, "asr.cloud.tencent.com/asr/v2/{appid}?排序后query"))}。
 */
public class TencentAsrSession implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(TencentAsrSession.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String HOST = "asr.cloud.tencent.com/asr/v2/";
    private static final String WSS_URL = "wss://asr.cloud.tencent.com/asr/v2/";

    /** 识别结果回调 */
    public interface Listener {
        void onPartialResult(String text);

        void onFinalResult(String text);

        void onError(String message);
    }

    private final WebSocket webSocket;
    private final Listener listener;
    private final StringBuilder finalText = new StringBuilder();
    private final AtomicBoolean finished = new AtomicBoolean(false);

    private TencentAsrSession(WebSocket webSocket, Listener listener) {
        this.webSocket = webSocket;
        this.listener = listener;
    }

    /**
     * 建立实时识别连接（阻塞直到握手完成）。
     */
    public static TencentAsrSession open(String secretId, String secretKey, String appId,
                                         Listener listener, HttpClient httpClient) {
        String url = buildUrl(secretId, secretKey, appId);
        final TencentAsrSession[] holder = new TencentAsrSession[1];

        WebSocket ws = httpClient.newWebSocketBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .buildAsync(URI.create(url), new WebSocket.Listener() {
                    @Override
                    public void onOpen(WebSocket webSocket) {
                        webSocket.request(1);
                    }

                    @Override
                    public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
                        if (holder[0] != null) {
                            holder[0].handleMessage(data.toString());
                        }
                        webSocket.request(1);
                        return null;
                    }

                    @Override
                    public void onError(WebSocket webSocket, Throwable error) {
                        if (holder[0] != null) {
                            holder[0].listener.onError("实时语音识别连接错误: "
                                    + (error == null ? "unknown" : error.getMessage()));
                        }
                    }

                    @Override
                    public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
                        // 服务器关闭但未收到 final：把已累积文本作为最终结果兜底
                        if (holder[0] != null) {
                            holder[0].finish(holder[0].finalText.toString());
                        }
                        return null;
                    }
                })
                .join();

        TencentAsrSession session = new TencentAsrSession(ws, listener);
        holder[0] = session;
        return session;
    }

    /** 推送一段 PCM 音频（16k/16bit/单声道） */
    public void sendAudio(byte[] pcm16k) {
        if (webSocket != null && !webSocket.isOutputClosed() && pcm16k != null && pcm16k.length > 0) {
            webSocket.sendBinary(ByteBuffer.wrap(pcm16k), true);
        }
    }

    /** 发送结束标记，服务端收到后返回最终识别结果 */
    public void stop() {
        if (webSocket != null && !webSocket.isOutputClosed()) {
            webSocket.sendText("{\"type\":\"end\"}", true);
        }
    }

    @Override
    public void close() {
        if (webSocket != null) {
            try {
                if (!webSocket.isInputClosed()) {
                    webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "done");
                }
            } catch (Exception ignored) {
                webSocket.abort();
            }
        }
    }

    private void handleMessage(String text) {
        log.debug("ASR 消息: {}", text);
        try {
            JsonNode node = MAPPER.readTree(text);
            int code = node.path("code").asInt(0);
            if (code != 0) {
                String msg = node.path("message").asText("识别失败");
                String hint = (msg.contains("AppID") || msg.contains("AppId"))
                        ? "（请确认设置页 AppId 与控制台「账号信息→APPID」一致，且 SecretId/SecretKey 与该 AppId 属同一账号）"
                        : "";
                listener.onError("实时语音识别失败: " + msg + hint);
                finish(finalText.toString());
                return;
            }

            // 整段识别结束标记（官方字段为 end，非 final）
            if (node.path("end").asInt(0) == 1) {
                // 极短语音可能无中间分句，此时取完成消息携带的文本兜底
                if (finalText.length() == 0) {
                    JsonNode r = node.path("result");
                    if (!r.isMissingNode() && !r.isNull()) {
                        finalText.append(r.path("voice_text_str").asText(""));
                    }
                }
                finish(finalText.toString());
                return;
            }

            JsonNode result = node.path("result");
            if (result.isMissingNode() || result.isNull()) {
                return; // ready ack
            }
            // slice_type 语义（对齐官方 SDK）：0=分句开始，1=中间结果，2=分句稳定结束
            int sliceType = result.path("slice_type").asInt(0);
            String voiceText = result.path("voice_text_str").asText("");
            if (sliceType == 2) {
                // 稳定分句，累加
                if (!voiceText.isEmpty()) {
                    finalText.append(voiceText);
                }
            } else if (sliceType == 1) {
                // 中间结果（会变化），仅作实时展示，不累加
                listener.onPartialResult(voiceText);
            }
            // sliceType == 0 忽略
        } catch (Exception e) {
            listener.onError("实时语音识别结果解析失败: " + e.getMessage());
        }
    }

    private void finish(String text) {
        if (finished.compareAndSet(false, true)) {
            log.info("实时识别最终结果: {}", text);
            listener.onFinalResult(text);
        }
    }

    /** 构造带签名的连接 URL（参数按 key 升序，签名原文 = host + appid + "?" + 排序query） */
    static String buildUrl(String secretId, String secretKey, String appId) {
        long now = Instant.now().getEpochSecond();
        long expired = now + 3600;
        int nonce = ThreadLocalRandom.current().nextInt(1000000, 10000000);
        TreeMap<String, String> params = new TreeMap<>();
        params.put("secretid", secretId);
        params.put("timestamp", String.valueOf(now));
        params.put("expired", String.valueOf(expired));
        params.put("nonce", String.valueOf(nonce));
        params.put("engine_model_type", "16k_zh");
        params.put("voice_format", "1");
        params.put("voice_id", UUID.randomUUID().toString().replace("-", ""));

        StringBuilder query = new StringBuilder();
        for (var e : params.entrySet()) {
            if (query.length() > 0) {
                query.append('&');
            }
            query.append(e.getKey()).append('=').append(e.getValue());
        }
        String signStr = HOST + appId + "?" + query;
        String signature = base64HmacSha1(secretKey, signStr);
        return WSS_URL + appId + "?" + query + "&signature=" + urlEncode(signature);
    }

    static String base64HmacSha1(String key, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA1"));
            return Base64.getEncoder().encodeToString(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("HMAC-SHA1 计算失败", e);
        }
    }

    static String urlEncode(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
