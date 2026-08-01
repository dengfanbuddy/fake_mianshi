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
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

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

    @Override
    public String recognizeSpeech(byte[] audioData) {
        validateConfigured();
        try {
            ObjectNode root = OBJECT_MAPPER.createObjectNode();
            root.put("Action", "SentenceRecognition");
            root.put("Version", ASR_VERSION);
            root.put("Region", properties.getRegion());
            root.put("ProjectId", 0);
            root.put("SubServiceType", 2);
            root.put("EngSerViceType", "16k_zh");
            root.put("SourceType", 1);
            root.put("VoiceFormat", "wav");
            root.put("Data", Base64.getEncoder().encodeToString(audioData));
            String payload = OBJECT_MAPPER.writeValueAsString(root);

            String responseBody = doPost(properties.getAsrUrl(), ASR_SERVICE, "SentenceRecognition", ASR_VERSION, payload);

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
            ObjectNode root = OBJECT_MAPPER.createObjectNode();
            root.put("Action", "TextToVoice");
            root.put("Version", TTS_VERSION);
            root.put("Text", text);
            root.put("SessionId", UUID.randomUUID().toString());
            root.put("ModelType", 1);
            root.put("VoiceType", Integer.parseInt(mapVoiceType(voiceType)));
            root.put("Codec", "wav");
            root.put("Speed", 0);
            root.put("Volume", 0);
            String payload = OBJECT_MAPPER.writeValueAsString(root);

            String responseBody = doPost(properties.getTtsUrl(), TTS_SERVICE, "TextToVoice", TTS_VERSION, payload);

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

    /**
     * 根据面试官风格配置（JSON）映射腾讯云 TTS 音色编号（新版 2019-08-23 音色 ID）。
     *
     * <p>从 styleConfig 中解析 tone 字段：serious → 1004（智云·男声）、
     * stern → 1010（智华·成熟男声）、warm → 1002（智聆·亲切女声）、
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
            String tone = node.path("tone").asText("").trim().toLowerCase();
            return switch (tone) {
                case "serious" -> "1004";
                case "stern" -> "1010";
                case "warm" -> "1002";
                case "neutral" -> DEFAULT_VOICE_TYPE;
                default -> DEFAULT_VOICE_TYPE;
            };
        } catch (JacksonException e) {
            return DEFAULT_VOICE_TYPE;
        }
    }

    /**
     * 校验腾讯云密钥已配置，未配置时抛出业务异常。
     */
    private void validateConfigured() {
        if (!properties.isConfigured()) {
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
     * 发起签名后的 POST 请求，返回响应体字符串；非 2xx 状态码时抛出业务异常。
     */
    private String doPost(String url, String service, String action, String version, String payload) {
        String timestamp = String.valueOf(Instant.now().getEpochSecond());
        String host = URI.create(url).getHost();
        String authorization = tc3Sign(properties.getSecretId(), properties.getSecretKey(),
                service, host, action, version, timestamp, payload, properties.getRegion());

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json; charset=utf-8")
                .header("X-TC-Action", action)
                .header("X-TC-Version", version)
                .header("X-TC-Timestamp", timestamp)
                .header("X-TC-Region", properties.getRegion())
                .header("Authorization", authorization)
                .timeout(Duration.ofSeconds(60))
                .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
                .build();

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
