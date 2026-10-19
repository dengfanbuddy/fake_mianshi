package com.fakemianshi.service;

import com.fakemianshi.config.BusinessException;
import com.fakemianshi.config.VoiceProperties;
import org.junit.jupiter.api.Test;

import java.net.ConnectException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 本地 Qwen3-ASR 客户端测试：全部 mock HttpClient，不依赖 sidecar。
 *
 * <p>覆盖请求形态（URL 查询参数、采样率头）与响应解析（文本/语种/RTF、错误码、静音短路、
 * 连不上时的可操作提示），以及 {@code /health} 里 asr 字段的透出。
 */
class LocalQwenVoiceServiceAsrTest {

    /** 构造一个总是返回给定响应体的 HttpClient，并把发出去的请求留在 captured 里 */
    private HttpClient client(int status, String body, AtomicReference<HttpRequest> captured) {
        HttpClient httpClient = mock(HttpClient.class);
        HttpResponse<byte[]> response = mock(HttpResponse.class);
        try {
            when(response.statusCode()).thenReturn(status);
            when(response.body()).thenReturn(body.getBytes(StandardCharsets.UTF_8));
            when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                    .thenAnswer(invocation -> {
                        if (captured != null) {
                            captured.set(invocation.getArgument(0));
                        }
                        return response;
                    });
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        return httpClient;
    }

    @Test
    void transcribe_shouldParseTextLanguageAndRtf() {
        LocalQwenVoiceService service = new LocalQwenVoiceService(new VoiceProperties(),
                client(200, """
                        {"text":"缓存穿透可以用布隆过滤器","language":"Chinese",
                         "audio_seconds":3.6,"infer_ms":614.0,"rtf":5.87,"skipped":null}
                        """, null));

        LocalQwenVoiceService.AsrResult result = service.transcribe(new byte[]{1, 2, 3}, true, 16000);
        assertEquals("缓存穿透可以用布隆过滤器", result.text());
        assertEquals("Chinese", result.language());
        assertEquals(3.6, result.audioSeconds());
        assertEquals(5.87, result.rtf());
        assertFalse(result.skipped());
    }

    @Test
    void transcribe_shouldSendWavContentTypeAndSkipSampleRateHeader() {
        AtomicReference<HttpRequest> captured = new AtomicReference<>();
        LocalQwenVoiceService service = new LocalQwenVoiceService(new VoiceProperties(),
                client(200, "{\"text\":\"ok\"}", captured));

        service.transcribe(new byte[]{1, 2, 3}, true, 16000);
        HttpRequest request = captured.get();
        assertTrue(request.uri().toString().endsWith("/asr"), request.uri().toString());
        assertEquals("audio/wav", request.headers().firstValue("Content-Type").orElse(""));
        assertTrue(request.headers().firstValue("X-Sample-Rate").isEmpty());
    }

    @Test
    void transcribe_shouldSendRawPcmWithSampleRateHeader() {
        AtomicReference<HttpRequest> captured = new AtomicReference<>();
        LocalQwenVoiceService service = new LocalQwenVoiceService(new VoiceProperties(),
                client(200, "{\"text\":\"ok\"}", captured));

        service.transcribe(new byte[]{1, 2, 3}, false, 8000);
        HttpRequest request = captured.get();
        assertEquals("application/octet-stream",
                request.headers().firstValue("Content-Type").orElse(""));
        assertEquals("8000", request.headers().firstValue("X-Sample-Rate").orElse(""));
    }

    @Test
    void transcribe_shouldPassLanguageAndPromptAsQueryParams() {
        VoiceProperties props = new VoiceProperties();
        props.getLocal().getAsr().setLanguage("Chinese");
        props.getLocal().getAsr().setPrompt("术语：Redis、幂等");
        AtomicReference<HttpRequest> captured = new AtomicReference<>();

        new LocalQwenVoiceService(props, client(200, "{\"text\":\"ok\"}", captured))
                .transcribe(new byte[]{1}, true, 16000);

        String uri = captured.get().uri().toString();
        assertTrue(uri.contains("language=Chinese"), uri);
        // 中文必须做 URL 编码，否则 uvicorn 会因为裸的非 ASCII 字符返回 400
        assertTrue(uri.contains("prompt=%E6%9C%AF%E8%AF%AD"), uri);
    }

    @Test
    void transcribe_shouldOmitQueryParams_whenNotConfigured() {
        AtomicReference<HttpRequest> captured = new AtomicReference<>();
        new LocalQwenVoiceService(new VoiceProperties(), client(200, "{\"text\":\"ok\"}", captured))
                .transcribe(new byte[]{1}, true, 16000);
        assertFalse(captured.get().uri().toString().contains("?"), captured.get().uri().toString());
    }

    @Test
    void recognizeSpeech_shouldReturnEmptyForEmptyAudio_withoutCallingHttp() {
        VoiceProperties props = new VoiceProperties();
        // baseUrl 指向必然拒绝连接的端口；如果没有短路，这里会抛 BusinessException
        props.getLocal().setBaseUrl("http://127.0.0.1:1");
        LocalQwenVoiceService service = new LocalQwenVoiceService(props);
        assertEquals("", service.recognizeSpeech(new byte[0]));
        assertEquals("", service.recognizeSpeech(null));
    }

    @Test
    void transcribe_shouldThrowBusinessException_whenSidecarReturnsError() {
        LocalQwenVoiceService service = new LocalQwenVoiceService(new VoiceProperties(),
                client(500, "{\"detail\":\"识别失败: CUDA out of memory\"}", null));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.recognizeSpeech(new byte[]{1, 2, 3}));
        assertTrue(ex.getMessage().contains("本地语音识别失败"), ex.getMessage());
        assertTrue(ex.getMessage().contains("CUDA out of memory"), ex.getMessage());
    }

    @Test
    void transcribe_shouldSurfaceActionableHint_whenSidecarUnreachable() throws Exception {
        HttpClient broken = mock(HttpClient.class);
        when(broken.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenThrow(new ConnectException("Connection refused"));

        LocalQwenVoiceService service = new LocalQwenVoiceService(new VoiceProperties(), broken);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.recognizeSpeech(new byte[]{1, 2, 3}));
        assertTrue(ex.getMessage().contains("voice-service 已启动"), ex.getMessage());
    }

    @Test
    void transcribe_shouldMarkSkipped_whenSidecarShortCircuits() {
        LocalQwenVoiceService service = new LocalQwenVoiceService(new VoiceProperties(),
                client(200, """
                        {"text":"","language":null,"audio_seconds":0.25,"infer_ms":0,
                         "rtf":0,"skipped":"silence-or-too-short"}
                        """, null));

        LocalQwenVoiceService.AsrResult result = service.transcribe(new byte[]{1, 2}, false, 16000);
        assertTrue(result.skipped());
        assertEquals("", result.text());
        assertNull(result.language());
    }

    @Test
    void health_shouldExposeAsrFields() {
        HttpClient httpClient = mock(HttpClient.class);
        HttpResponse<String> response = mock(HttpResponse.class);
        try {
            when(response.statusCode()).thenReturn(200);
            when(response.body()).thenReturn("""
                    {"status":"ok","loaded":true,"kind":"faster","model":"tts-model","device":"cuda",
                     "speakers":["serena"],"load_seconds":12.0,"load_error":null,
                     "asr":{"enabled":true,"loaded":true,"model":"Qwen3-ASR-0.6B","device":"cuda",
                            "language":"Chinese","load_error":null}}
                    """);
            when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                    .thenReturn(response);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }

        var health = new LocalQwenVoiceService(new VoiceProperties(), httpClient).health();
        assertEquals(true, health.get("asrEnabled"));
        assertEquals(true, health.get("asrLoaded"));
        assertEquals("Qwen3-ASR-0.6B", health.get("asrModel"));
        assertEquals("Chinese", health.get("asrLanguage"));
    }
}
