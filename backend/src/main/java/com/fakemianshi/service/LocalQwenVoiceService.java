package com.fakemianshi.service;

import com.fakemianshi.config.BusinessException;
import com.fakemianshi.config.VoiceProperties;
import com.fakemianshi.entity.VoiceConfig;
import com.fakemianshi.util.SentenceSplitter;
import com.fakemianshi.util.WavAudio;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 本地 Qwen3-TTS 语音合成实现：调用 voice-service（Python sidecar）的 HTTP 接口。
 *
 * <p>接口契约见 {@code voice-service/app/main.py}：{@code POST /tts} 返回 16k/16bit/单声道 wav，
 * {@code POST /tts/stream} 返回 16k PCM 裸流分块，{@code GET /health} 返回模型状态。
 *
 * <p>面试官人设（{@code styleConfig}）在这里被翻译成自然语言风格指令（instruct），
 * 而不再是腾讯云的音色编号——这是本地 Qwen3-TTS 相对云服务的核心收益。
 */
@Slf4j
@Component
public class LocalQwenVoiceService implements VoiceService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 人设 tone → 默认音色 + 风格指令（Qwen3-TTS CustomVoice 的 9 个预置音色，名字必须小写） */
    private static final Map<String, String> TONE_SPEAKER = Map.of(
            "serious", "uncle_fu",
            "stern", "dylan",
            "warm", "serena",
            "professional", "uncle_fu",
            "neutral", "vivian");

    private static final Map<String, String> TONE_INSTRUCT = Map.of(
            "serious", "冷静、专业、略带审视的技术面试官，语气克制而有分量",
            "stern", "强势、直接的面试官，步步紧逼，带一点压迫感",
            "warm", "温和亲切、鼓励式的资深工程师，语气柔和",
            "professional", "成熟稳重、注重细节的项目面试官，表达清晰",
            "neutral", "标准的技术面试官，语气平稳自然");

    private static final String DEFAULT_SPEAKER = "uncle_fu";
    private static final String DEFAULT_INSTRUCT = "标准的技术面试官，语气平稳自然";

    private final VoiceProperties properties;
    private final HttpClient httpClient;

    @Autowired(required = false)
    private VoiceConfigService voiceConfigService;

    /** 生产环境构造器：Spring 使用此构造器（另一个为测试用） */
    @Autowired
    public LocalQwenVoiceService(VoiceProperties properties) {
        this(properties, newHttpClient(properties));
    }

    /**
     * 强制 HTTP/1.1：JDK HttpClient 默认会先发 h2c 升级请求（{@code Upgrade: h2c}），
     * uvicorn/starlette 不支持该升级，会把请求体丢掉并返回 422。
     */
    private static HttpClient newHttpClient(VoiceProperties properties) {
        return HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(properties.getLocal().getConnectTimeoutSeconds()))
                .build();
    }

    /** 测试用构造器：可注入 mock HttpClient */
    public LocalQwenVoiceService(VoiceProperties properties, HttpClient httpClient) {
        this.properties = properties;
        this.httpClient = httpClient;
    }

    // ---------------- VoiceService ----------------

    @Override
    public String recognizeSpeech(byte[] audioData) {
        return transcribe(audioData, true, properties.getLocal().getSampleRate()).text();
    }

    /**
     * 识别一段音频，返回文本 + 语种 + 性能指标。
     *
     * @param audio      音频字节；{@code wav=true} 时是带 RIFF 头的文件，否则是裸 PCM16LE
     * @param wav        是否带容器头
     * @param sampleRate 裸 PCM 的采样率（带头的以文件为准）
     */
    public AsrResult transcribe(byte[] audio, boolean wav, int sampleRate) {
        if (audio == null || audio.length == 0) {
            return new AsrResult("", null, 0, 0, true);
        }
        VoiceProperties.Asr asr = properties.getLocal().getAsr();
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(asrUrl(asr)))
                .timeout(Duration.ofSeconds(asr.getTimeoutSeconds()))
                .header("Content-Type", wav ? "audio/wav" : "application/octet-stream");
        if (!wav) {
            builder.header("X-Sample-Rate", String.valueOf(sampleRate));
        }
        HttpRequest request = builder.POST(HttpRequest.BodyPublishers.ofByteArray(audio)).build();

        HttpResponse<byte[]> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new BusinessException("本地语音识别调用失败: " + e.getMessage()
                    + "（请确认 voice-service 已启动：设置页「启动本地服务」或 voice-service/run.ps1）", e);
        }
        int status = response.statusCode();
        if (status < 200 || status >= 300) {
            String detail = new String(response.body(), StandardCharsets.UTF_8);
            throw new BusinessException("本地语音识别失败，状态码: " + status + "，响应: " + detail);
        }
        try {
            JsonNode node = MAPPER.readTree(response.body());
            return new AsrResult(
                    node.path("text").asText(""),
                    node.path("language").isNull() ? null : node.path("language").asText(null),
                    node.path("audio_seconds").asDouble(0),
                    node.path("rtf").asDouble(0),
                    !node.path("skipped").isNull() && !node.path("skipped").isMissingNode());
        } catch (JacksonException e) {
            throw new BusinessException("本地语音识别结果解析失败: " + e.getMessage(), e);
        }
    }

    /** 裸 PCM16 识别（WebSocket 路径直接用累积缓冲，省掉一次 WAV 封装） */
    public String recognizePcm16(byte[] pcm, int sampleRate) {
        return transcribe(pcm, false, sampleRate).text();
    }

    /** ``/asr`` 地址：带上配置里的语种与领域热词（都为空则不带参数） */
    private String asrUrl(VoiceProperties.Asr asr) {
        StringBuilder url = new StringBuilder(baseUrl()).append("/asr");
        char sep = '?';
        if (asr.getLanguage() != null && !asr.getLanguage().isBlank()) {
            url.append(sep).append("language=").append(urlEncode(asr.getLanguage().trim()));
            sep = '&';
        }
        String prompt = effectiveAsrPrompt();
        if (!prompt.isBlank()) {
            url.append(sep).append("prompt=").append(urlEncode(prompt));
        }
        return url.toString();
    }

    /**
     * 生效的识别热词：页面配置优先，其次配置文件/环境变量。
     *
     * <p>抽出来是为了可观测——识别不准时第一件事就是确认到底用了哪份热词。
     */
    public String effectiveAsrPrompt() {
        if (voiceConfigService != null) {
            // 服务内部已处理「页面为空 → 内置默认」的回退，这里直接用
            String resolved = voiceConfigService.effectiveAsrPrompt();
            return resolved == null ? "" : resolved.trim();
        }
        String fallback = properties.getLocal().getAsr().getPrompt();
        return fallback == null ? "" : fallback.trim();
    }

    private static String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    /** 识别结果：文本 + 语种 + 性能指标 */
    public record AsrResult(String text, String language, double audioSeconds, double rtf, boolean skipped) {
    }

    @Override
    public byte[] synthesizeSpeech(String text, String voiceType) {
        if (text == null || text.isBlank()) {
            return new byte[0];
        }
        PersonaVoice voice = resolvePersonaVoice(voiceType);
        ObjectNode body = MAPPER.createObjectNode();
        body.put("text", text);
        body.put("speaker", voice.speaker());
        body.put("instruct", voice.instruct());
        body.put("language", voice.language());
        String payload = MAPPER.writeValueAsString(body);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl() + "/tts"))
                .header("Content-Type", "application/json; charset=utf-8")
                .timeout(Duration.ofSeconds(properties.getLocal().getTimeoutSeconds()))
                .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
                .build();

        HttpResponse<byte[]> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new BusinessException("本地语音服务调用失败: " + e.getMessage()
                    + "（请确认 voice-service 已启动：voice-service/run.ps1）", e);
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            String detail = new String(response.body(), StandardCharsets.UTF_8);
            throw new BusinessException("本地语音合成失败，状态码: " + response.statusCode() + "，响应: " + detail);
        }
        byte[] wav = response.body();
        if (wav == null || wav.length <= 44) {
            throw new BusinessException("本地语音合成失败: 返回音频为空");
        }
        // sidecar 已重采样到目标采样率；若配置不一致则在此纠正头部，避免前端播放变速
        WavAudio.PcmInfo info = WavAudio.parse(wav);
        int target = properties.getLocal().getSampleRate();
        if (info != null && info.sampleRate() != target) {
            log.warn("本地 TTS 采样率 {} 与预期 {} 不一致，已重新封装 wav 头", info.sampleRate(), target);
            return WavAudio.pcmToWav(info.pcm(), target, info.channels(), info.bitsPerSample());
        }
        return wav;
    }

    @Override
    public byte[] synthesizeSpeechLong(String text, String voiceType) {
        if (text == null || text.isBlank()) {
            return new byte[0];
        }
        List<String> sentences = SentenceSplitter.split(text);
        List<byte[]> wavs = new ArrayList<>(sentences.size());
        for (String sentence : sentences) {
            byte[] wav = synthesizeSpeech(sentence, voiceType);
            if (wav != null && wav.length > 0) {
                wavs.add(wav);
            }
        }
        return WavAudio.concat(wavs);
    }

    @Override
    public void synthesizeSpeechStream(String text, String voiceType, Consumer<byte[]> onPcmChunk) {
        if (text == null || text.isBlank() || onPcmChunk == null) {
            return;
        }
        PersonaVoice voice = resolvePersonaVoice(voiceType);
        ObjectNode body = MAPPER.createObjectNode();
        body.put("text", text);
        body.put("speaker", voice.speaker());
        body.put("instruct", voice.instruct());
        body.put("language", voice.language());
        String payload = MAPPER.writeValueAsString(body);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl() + "/tts/stream"))
                .header("Content-Type", "application/json; charset=utf-8")
                .timeout(Duration.ofSeconds(properties.getLocal().getTimeoutSeconds()))
                .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
                .build();

        HttpResponse<InputStream> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new BusinessException("本地流式语音合成调用失败: " + e.getMessage(), e);
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new BusinessException("本地流式语音合成失败，状态码: " + response.statusCode());
        }
        try (InputStream in = response.body()) {
            byte[] buf = new byte[4096];
            // InputStream.read 可能返回任意长度，甚至奇数字节。奇数长度的分片意味着
            // 一个 16bit 样本被劈成两半：前端拼不回就会让之后所有样本错位一字节
            // （int16 被解释成噪声，听起来是刺耳白噪音），所以这里把半个样本留到下一块。
            int leftover = -1;
            int n;
            while ((n = in.read(buf)) != -1) {
                if (n <= 0) {
                    continue;
                }
                byte[] chunk;
                int len;
                if (leftover >= 0) {
                    len = n + 1;
                    chunk = new byte[len];
                    chunk[0] = (byte) leftover;
                    System.arraycopy(buf, 0, chunk, 1, n);
                    leftover = -1;
                } else {
                    len = n;
                    chunk = new byte[len];
                    System.arraycopy(buf, 0, chunk, 0, n);
                }
                if ((len & 1) != 0) {
                    leftover = chunk[len - 1] & 0xFF;
                    len--;
                }
                if (len > 0) {
                    byte[] aligned = (len == chunk.length) ? chunk : java.util.Arrays.copyOf(chunk, len);
                    onPcmChunk.accept(aligned);
                }
            }
            if (leftover >= 0) {
                // 整个流出现半个样本：音频本身已经损坏，丢掉这半字节比发出去更安全
                log.warn("流式语音末块残留半个样本（1 字节），已丢弃");
            }
        } catch (IOException e) {
            throw new BusinessException("本地流式语音合成响应读取失败: " + e.getMessage(), e);
        }
    }

    // ---------------- 辅助能力 ----------------

    /** 查询 sidecar 健康状态（页面「连通性测试」用） */
    public Map<String, Object> health() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("baseUrl", baseUrl());
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl() + "/health"))
                .timeout(Duration.ofSeconds(Math.min(10, properties.getLocal().getTimeoutSeconds())))
                .GET()
                .build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            result.put("reachable", response.statusCode() >= 200 && response.statusCode() < 300);
            result.put("statusCode", response.statusCode());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                JsonNode node = MAPPER.readTree(response.body());
                result.put("status", node.path("status").asText());
                result.put("loaded", node.path("loaded").asBoolean(false));
                result.put("kind", node.path("kind").asText());
                result.put("model", node.path("model").asText());
                result.put("device", node.path("device").asText());
                result.put("speakers", MAPPER.convertValue(node.path("speakers"), List.class));
                result.put("loadSeconds", node.path("load_seconds").isNull()
                        ? null : node.path("load_seconds").asDouble());
                result.put("loadError", node.path("load_error").isNull()
                        ? null : node.path("load_error").asText());
                JsonNode asrNode = node.path("asr");
                if (!asrNode.isMissingNode() && !asrNode.isNull()) {
                    result.put("asrEnabled", asrNode.path("enabled").asBoolean(false));
                    result.put("asrLoaded", asrNode.path("loaded").asBoolean(false));
                    result.put("asrModel", asrNode.path("model").asText(null));
                    result.put("asrDevice", asrNode.path("device").asText(null));
                    result.put("asrLanguage", asrNode.path("language").isNull()
                            ? null : asrNode.path("language").asText(null));
                    result.put("asrLoadError", asrNode.path("load_error").isNull()
                            ? null : asrNode.path("load_error").asText());
                }
            }
            return result;
        } catch (IOException | InterruptedException | JacksonException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            result.put("reachable", false);
            result.put("error", e.getMessage());
            return result;
        }
    }

    /** 当前生效的 sidecar 地址：页面配置（voice_config.tts_url）优先，回退配置文件 */
    public String baseUrl() {
        if (voiceConfigService != null) {
            VoiceConfig cfg = voiceConfigService.resolveActive();
            if (cfg != null && cfg.isLocalProvider()
                    && cfg.getTtsUrl() != null && !cfg.getTtsUrl().isBlank()) {
                return trimTrailingSlash(cfg.getTtsUrl());
            }
        }
        return trimTrailingSlash(properties.getLocal().getBaseUrl());
    }

    private static String trimTrailingSlash(String url) {
        return url != null && url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    /**
     * 把人设 styleConfig（JSON）翻译为音色 + 自然语言风格指令。
     *
     * <p>支持人设内显式覆盖：{@code qwenVoice} / {@code qwenInstruct} / {@code qwenLanguage}。
     */
    public PersonaVoice resolvePersonaVoice(String personaStyleConfig) {
        if (personaStyleConfig == null || personaStyleConfig.isBlank()) {
            return new PersonaVoice(DEFAULT_SPEAKER, DEFAULT_INSTRUCT, "Chinese");
        }
        try {
            JsonNode node = MAPPER.readTree(personaStyleConfig);
            String explicitSpeaker = node.path("qwenVoice").asText("").trim();
            String explicitInstruct = node.path("qwenInstruct").asText("").trim();
            String language = node.path("qwenLanguage").asText("Chinese").trim();
            if (language.isEmpty()) {
                language = "Chinese";
            }
            String tone = node.path("tone").asText("").trim().toLowerCase();
            String speaker = explicitSpeaker.isEmpty()
                    ? TONE_SPEAKER.getOrDefault(tone, DEFAULT_SPEAKER) : explicitSpeaker;

            String instruct = explicitInstruct.isEmpty()
                    ? TONE_INSTRUCT.getOrDefault(tone, DEFAULT_INSTRUCT) : explicitInstruct;
            if (explicitInstruct.isEmpty()) {
                String speed = node.path("speed").asText("").trim().toLowerCase();
                instruct = instruct + switch (speed) {
                    case "slow" -> "，语速偏慢";
                    case "fast" -> "，语速偏快";
                    default -> "，语速中等";
                };
                double aggressiveness = node.path("aggressiveness").asDouble(0);
                if (aggressiveness >= 0.8) {
                    instruct = instruct + "，追问犀利";
                }
            }
            return new PersonaVoice(speaker, instruct, language);
        } catch (JacksonException e) {
            log.warn("人设风格解析失败，使用默认音色: {}", e.getMessage());
            return new PersonaVoice(DEFAULT_SPEAKER, DEFAULT_INSTRUCT, "Chinese");
        }
    }

    /** 人设映射结果：音色 + 风格指令 + 语种 */
    public record PersonaVoice(String speaker, String instruct, String language) {
    }
}
