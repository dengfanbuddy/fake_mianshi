package com.fakemianshi.service.impl;

import com.fakemianshi.config.BusinessException;
import com.fakemianshi.config.TencentCloudProperties;
import com.fakemianshi.service.VoiceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import com.fakemianshi.util.SentenceSplitter;
import com.fakemianshi.util.WavAudio;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * 语音服务默认实现：通过 REST API 直连腾讯云（ASR 一句话识别 + TTS 基础语音合成）。
 *
 * <p>采用腾讯云 API 3.0 签名 v3（TC3-HMAC-SHA256），使用 Java 17 自带 {@link HttpClient}，
 * 不引入腾讯云 SDK 依赖，减少构建复杂度。
 */
@Service
public class VoiceServiceImpl implements VoiceService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final String ALGORITHM = "TC3-HMAC-SHA256";
    private static final String ASR_VERSION = "2019-06-14";
    /** TTS 基础语音合成接口版本（2019-08-23；旧版 2018-08-01 已停用） */
    private static final String TTS_VERSION = "2019-08-23";
    private static final String ASR_SERVICE = "asr";
    private static final String TTS_SERVICE = "tts";

    /** 默认音色：1004 智云（标准男声） */
    public static final String DEFAULT_VOICE_TYPE = "1004";

    private final TencentCloudProperties properties;
    private final HttpClient httpClient;

    /** 数据库语音配置（页面可配）；测试构造器不注入时回退环境变量 */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.fakemianshi.service.VoiceConfigService voiceConfigService;

    /**
     * 生产环境构造器：创建带超时配置的默认 HttpClient。
     */
    @Autowired
    public VoiceServiceImpl(TencentCloudProperties properties) {
        this(properties, HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build());
    }

    /**
     * 测试用构造器：可注入 mock 的 HttpClient，避免真实网络请求。
     */
    public VoiceServiceImpl(TencentCloudProperties properties, HttpClient httpClient) {
        this.properties = properties;
        this.httpClient = httpClient;
    }

    /** 解析当前生效的语音配置：数据库配置优先，回退环境变量 */
    private com.fakemianshi.entity.VoiceConfig resolveVoiceConfig() {
        if (voiceConfigService != null) {
            com.fakemianshi.entity.VoiceConfig cfg = voiceConfigService.resolveActive();
            if (cfg != null && cfg.isConfigured()) {
                return cfg;
            }
        }
        return null;
    }

    @Override
    public String recognizeSpeech(byte[] audioData) {
        validateConfigured();
        try {
            // 注意：Action/Version/Region 为公共参数，通过 X-TC-* 请求头传递（见 doPost），
            // 不能放进 JSON body——腾讯云新版接口会对 body 中的公共参数字段做严格校验。
            ObjectNode root = OBJECT_MAPPER.createObjectNode();
            root.put("ProjectId", 0);
            root.put("SubServiceType", 2);
            root.put("EngSerViceType", "16k_zh");
            root.put("SourceType", 1);
            root.put("VoiceFormat", "wav");
            root.put("Data", Base64.getEncoder().encodeToString(audioData));
            root.put("DataLen", audioData.length);
            String payload = OBJECT_MAPPER.writeValueAsString(root);

            com.fakemianshi.entity.VoiceConfig cfg = resolveVoiceConfig();
            String asrUrl = cfg != null ? cfg.getAsrUrl() : properties.getAsrUrl();
            String responseBody = doPost(asrUrl, ASR_SERVICE, "SentenceRecognition", ASR_VERSION, payload);

            JsonNode responseNode = OBJECT_MAPPER.readTree(responseBody).path("Response");
            JsonNode error = responseNode.path("Error");
            if (!error.isMissingNode()) {
                throw new BusinessException("语音识别失败: " + error.path("Message").asText());
            }
            return responseNode.path("Result").asText();
        } catch (JacksonException e) {
            throw new BusinessException("语音识别请求构建失败: " + e.getMessage(), e);
        }
    }

    @Override
    public byte[] synthesizeSpeech(String text, String voiceType) {
        validateConfigured();
        try {
            // Action/Version/Region 为公共参数，经 X-TC-* 请求头传递（见 doPost），不放入 body
            ObjectNode root = OBJECT_MAPPER.createObjectNode();
            root.put("Text", text);
            root.put("SessionId", UUID.randomUUID().toString());
            root.put("ModelType", 1);
            root.put("VoiceType", Integer.parseInt(mapVoiceType(voiceType)));
            root.put("Codec", "wav");
            root.put("Speed", mapSpeed(voiceType));
            root.put("Volume", 0);
            String payload = OBJECT_MAPPER.writeValueAsString(root);

            com.fakemianshi.entity.VoiceConfig cfg = resolveVoiceConfig();
            String ttsUrl = cfg != null ? cfg.getTtsUrl() : properties.getTtsUrl();
            String responseBody = doPost(ttsUrl, TTS_SERVICE, "TextToVoice", TTS_VERSION, payload);

            JsonNode responseNode = OBJECT_MAPPER.readTree(responseBody).path("Response");
            JsonNode error = responseNode.path("Error");
            if (!error.isMissingNode()) {
                throw new BusinessException("语音合成失败: " + error.path("Message").asText());
            }
            String audioBase64 = responseNode.path("Audio").asText();
            if (audioBase64.isBlank()) {
                throw new BusinessException("语音合成失败: 响应中未包含音频数据");
            }
            return Base64.getDecoder().decode(audioBase64);
        } catch (JacksonException e) {
            throw new BusinessException("语音合成请求构建失败: " + e.getMessage(), e);
        } catch (IllegalArgumentException e) {
            throw new BusinessException("语音合成音频解码失败: " + e.getMessage(), e);
        }
    }

    @Override
    public byte[] synthesizeSpeechLong(String text, String voiceType) {
        if (text == null || text.isBlank()) {
            return new byte[0];
        }
        List<String> sentences = SentenceSplitter.split(text);
        if (sentences.isEmpty()) {
            return new byte[0];
        }
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
        validateConfigured();
        if (text == null || text.isBlank() || onPcmChunk == null) {
            return;
        }
        try {
            ObjectNode root = OBJECT_MAPPER.createObjectNode();
            root.put("Text", text);
            root.put("SessionId", UUID.randomUUID().toString());
            root.put("ModelType", 1);
            root.put("VoiceType", Integer.parseInt(mapVoiceType(voiceType)));
            root.put("Codec", "pcm");
            root.put("Speed", mapSpeed(voiceType));
            root.put("Volume", 0);
            String payload = OBJECT_MAPPER.writeValueAsString(root);

            com.fakemianshi.entity.VoiceConfig cfg = resolveVoiceConfig();
            String ttsUrl = cfg != null ? cfg.getTtsUrl() : properties.getTtsUrl();
            doPostStreaming(ttsUrl, TTS_SERVICE, "TextToStreamAudio", TTS_VERSION, payload, onPcmChunk);
        } catch (JacksonException e) {
            throw new BusinessException("语音合成请求构建失败: " + e.getMessage(), e);
        } catch (IllegalArgumentException e) {
            throw new BusinessException("语音合成参数错误: " + e.getMessage(), e);
        }
    }

    /**
     * 根据面试官风格配置（JSON）映射腾讯云 TTS 音色编号（新版 2019-08-23 音色 ID）。
     *
     * <p>从 styleConfig 中解析 tone 字段映射腾讯云音色：
     * serious → 1010（智华·成熟男声，技术深挖）、stern → 1004（智云·男声，压力面试）、
     * warm → 1002（智聆·亲切女声，温和引导）、professional → 1010（智华·成熟男声，项目实战）、
     * neutral/未知 → 1004（智云·标准男声）。
     *
     * @param personaStyleConfig 人设风格配置 JSON 字符串
     * @return 腾讯云 VoiceType 数值字符串
     */
    public String mapVoiceType(String personaStyleConfig) {
        if (personaStyleConfig == null || personaStyleConfig.isBlank()) {
            return DEFAULT_VOICE_TYPE;
        }
        try {
            JsonNode node = OBJECT_MAPPER.readTree(personaStyleConfig);
            // 人设 styleConfig 里显式指定 voiceType 时优先使用（便于按人设精调音色）
            String explicit = node.path("voiceType").asText("").trim();
            if (!explicit.isEmpty()) {
                return explicit;
            }
            String tone = node.path("tone").asText("").trim().toLowerCase();
            return switch (tone) {
                case "serious" -> "1018";       // 智靖·情感男声（严肃深挖，有感情）
                case "stern" -> "1010";         // 智华·通用男声（严厉施压）
                case "warm" -> "1001";          // 智瑜·情感女声（温和引导，亲切）
                case "professional" -> "1004";  // 智云·通用男声（项目实战，专业）
                case "neutral" -> "1009";       // 智芸·知性女声（八股文标准考察）
                default -> DEFAULT_VOICE_TYPE;
            };
        } catch (JacksonException e) {
            return DEFAULT_VOICE_TYPE;
        }
    }

    /**
     * 从人设 styleConfig 中解析语速（slow/medium/fast），映射为腾讯云 TTS Speed（-2~2）。
     */
    public int mapSpeed(String personaStyleConfig) {
        if (personaStyleConfig == null || personaStyleConfig.isBlank()) {
            return 0;
        }
        try {
            JsonNode node = OBJECT_MAPPER.readTree(personaStyleConfig);
            String speed = node.path("speed").asText("").trim().toLowerCase();
            return switch (speed) {
                case "slow" -> -1;
                case "fast" -> 1;
                default -> 0;
            };
        } catch (JacksonException e) {
            return 0;
        }
    }

    /**
     * 校验腾讯云密钥已配置，未配置时抛出业务异常。
     */
    private void validateConfigured() {
        if (resolveVoiceConfig() == null && !properties.isConfigured()) {
            throw new BusinessException("未配置腾讯云语音密钥，请在设置中配置");
        }
    }

    /**
     * 生成腾讯云 API 3.0 签名 v3（TC3-HMAC-SHA256）的 Authorization 请求头。
     *
     * <p>流程：构造 CanonicalRequest → 拼接 StringToSign → 用 HMAC-SHA256 逐级派生
     * 签名密钥（SecretDate/SecretService/SecretSigning）→ 计算 Signature。
     * 按官方文档示例签名 content-type、host 和 x-tc-action 三个请求头。
     *
     * @param secretId  腾讯云 SecretId
     * @param secretKey 腾讯云 SecretKey
     * @param service   服务名，如 asr / tts
     * @param host      请求 host，如 asr.tencentcloudapi.com
     * @param action    接口名，如 SentenceRecognition
     * @param version   接口版本，如 2019-06-14（随 X-TC-Version 头发送，不参与签名）
     * @param timestamp 秒级时间戳
     * @param payload   JSON 请求体
     * @param region    地域，如 ap-guangzhou（随 X-TC-Region 头发送，不参与签名）
     * @return Authorization 请求头值
     */
    public String tc3Sign(String secretId, String secretKey, String service, String host,
                          String action, String version, String timestamp, String payload, String region) {
        String date = Instant.ofEpochSecond(Long.parseLong(timestamp))
                .atZone(ZoneOffset.UTC)
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        String credentialScope = date + "/" + service + "/tc3_request";

        // 1. CanonicalRequest（头部按 key 小写、ASCII 升序拼接）
        String xTcAction = action.toLowerCase();
        String canonicalHeaders = "content-type:application/json; charset=utf-8\n"
                + "host:" + host + "\n"
                + "x-tc-action:" + xTcAction + "\n";
        String signedHeaders = "content-type;host;x-tc-action";
        String canonicalRequest = "POST\n/\n\n" + canonicalHeaders + "\n" + signedHeaders + "\n" + sha256Hex(payload);

        // 2. StringToSign
        String stringToSign = ALGORITHM + "\n" + timestamp + "\n" + credentialScope + "\n" + sha256Hex(canonicalRequest);

        // 3. 派生签名密钥并计算 Signature
        byte[] secretDate = hmacSha256(("TC3" + secretKey).getBytes(StandardCharsets.UTF_8), date);
        byte[] secretService = hmacSha256(secretDate, service);
        byte[] secretSigning = hmacSha256(secretService, "tc3_request");
        byte[] signatureBytes = hmacSha256(secretSigning, stringToSign);
        String signature = HexFormat.of().formatHex(signatureBytes);

        return ALGORITHM + " Credential=" + secretId + "/" + credentialScope
                + ", SignedHeaders=" + signedHeaders
                + ", Signature=" + signature;
    }

    /**
     * 构建带 TC3 签名的 POST 请求（公共参数经 X-TC-* 请求头传递，不入 body）。
     */
    private HttpRequest buildSignedRequest(String url, String service, String action, String version, String payload) {
        String timestamp = String.valueOf(Instant.now().getEpochSecond());
        String host = URI.create(url).getHost();
        com.fakemianshi.entity.VoiceConfig cfg = resolveVoiceConfig();
        String secretId = cfg != null ? cfg.getSecretId() : properties.getSecretId();
        String secretKey = cfg != null ? cfg.getSecretKey() : properties.getSecretKey();
        String region = cfg != null ? cfg.getRegion() : properties.getRegion();
        String authorization = tc3Sign(secretId, secretKey,
                service, host, action, version, timestamp, payload, region);

        return HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json; charset=utf-8")
                .header("X-TC-Action", action)
                .header("X-TC-Version", version)
                .header("X-TC-Timestamp", timestamp)
                .header("X-TC-Region", region)
                .header("Authorization", authorization)
                .timeout(Duration.ofSeconds(60))
                .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
                .build();
    }

    /**
     * 发起签名后的 POST 请求，返回响应体字符串；非 2xx 状态码时抛出业务异常。
     */
    private String doPost(String url, String service, String action, String version, String payload) {
        HttpRequest request = buildSignedRequest(url, service, action, version, payload);

        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new BusinessException("腾讯云语音服务调用失败: " + e.getMessage(), e);
        }

        int statusCode = response.statusCode();
        if (statusCode < 200 || statusCode >= 300) {
            throw new BusinessException("腾讯云语音服务调用失败，状态码: " + statusCode + "，响应: " + response.body());
        }
        return response.body();
    }

    /**
     * 发起签名后的流式 POST 请求（实时语音合成 TextToStreamAudio），
     * 边读响应边回调 PCM 分片；非 2xx 状态码时读取错误体并抛出业务异常。
     */
    private void doPostStreaming(String url, String service, String action, String version, String payload,
                                 Consumer<byte[]> onPcmChunk) {
        HttpRequest request = buildSignedRequest(url, service, action, version, payload);

        HttpResponse<java.io.InputStream> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new BusinessException("腾讯云语音服务调用失败: " + e.getMessage(), e);
        }

        int statusCode = response.statusCode();
        if (statusCode < 200 || statusCode >= 300) {
            String errBody = readBody(response.body());
            throw new BusinessException("腾讯云语音服务调用失败，状态码: " + statusCode + "，响应: " + errBody);
        }

        try (java.io.InputStream in = response.body()) {
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) != -1) {
                if (n > 0) {
                    byte[] chunk = new byte[n];
                    System.arraycopy(buf, 0, chunk, 0, n);
                    onPcmChunk.accept(chunk);
                }
            }
        } catch (IOException e) {
            throw new BusinessException("流式语音合成响应读取失败: " + e.getMessage(), e);
        }
    }

    /** 读取错误响应体为字符串，读取失败返回空串 */
    private String readBody(java.io.InputStream in) {
        try {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "";
        }
    }

    /** SHA-256 摘要，返回小写十六进制 */
    private static String sha256Hex(String data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(data.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }

    /** HMAC-SHA256 计算 */
    private static byte[] hmacSha256(byte[] key, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("HMAC-SHA256 计算失败", e);
        }
    }
}
