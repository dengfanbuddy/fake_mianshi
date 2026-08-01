package com.fakemianshi.service;

import com.fakemianshi.config.BusinessException;
import com.fakemianshi.config.TencentCloudProperties;
import com.fakemianshi.service.impl.VoiceServiceImpl;
import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 语音服务单元测试：不依赖真实网络，未配置密钥场景与签名/音色映射为纯逻辑验证，
 * 网络请求场景 mock HttpClient。
 */
class VoiceServiceTest {

    private TencentCloudProperties unconfigured() {
        TencentCloudProperties props = new TencentCloudProperties();
        props.setSecretId("");
        props.setSecretKey("");
        return props;
    }

    private TencentCloudProperties configured() {
        TencentCloudProperties props = new TencentCloudProperties();
        props.setSecretId("AKID_TEST");
        props.setSecretKey("SECRET_TEST");
        return props;
    }

    @Test
    void recognizeSpeech_shouldThrowBusinessException_whenNotConfigured() {
        VoiceService service = new VoiceServiceImpl(unconfigured());
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.recognizeSpeech(new byte[]{1, 2, 3}));
        assertEquals("未配置腾讯云语音密钥，请在设置中配置", ex.getMessage());
    }

    @Test
    void synthesizeSpeech_shouldThrowBusinessException_whenNotConfigured() {
        VoiceService service = new VoiceServiceImpl(unconfigured());
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.synthesizeSpeech("你好，请自我介绍", "{\"tone\":\"neutral\"}"));
        assertEquals("未配置腾讯云语音密钥，请在设置中配置", ex.getMessage());
    }

    @Test
    void tc3Sign_shouldProduceWellFormedAuthorizationHeader() {
        VoiceServiceImpl service = new VoiceServiceImpl(configured());
        String auth = service.tc3Sign("AKIDz8kBbsJj4S1q2tX2hUQ6hT9a1sExample",
                "Gu5t9xGARNpq86cd98joQYCN3Example",
                "asr", "asr.tencentcloudapi.com",
                "SentenceRecognition", "2019-06-14",
                "1551113065", "{\"ProjectId\":0,\"SubServiceType\":2}", "ap-guangzhou");
        assertTrue(auth.startsWith("TC3-HMAC-SHA256 "));
        assertTrue(auth.contains("Credential="));
        assertTrue(auth.contains("SignedHeaders=content-type;host;x-tc-action"));
        assertTrue(auth.contains("Signature="));
    }

    @Test
    void tc3Sign_shouldMatchGoldenReferenceValue() {
        // 金标值由独立 Python 参考实现（hashlib/hmac）对相同输入计算得出，用于校验 TC3 算法正确性
        VoiceServiceImpl service = new VoiceServiceImpl(configured());
        String auth = service.tc3Sign("AKIDz8kBbsJj4S1q2tX2hUQ6hT9a1sExample",
                "Gu5t9xGARNpq86cd98joQYCN3Example",
                "asr", "asr.tencentcloudapi.com",
                "SentenceRecognition", "2019-06-14",
                "1551113065", "{\"ProjectId\":0,\"SubServiceType\":2}", "ap-guangzhou");
        assertEquals("TC3-HMAC-SHA256 Credential=AKIDz8kBbsJj4S1q2tX2hUQ6hT9a1sExample/2019-02-25/asr/tc3_request, "
                + "SignedHeaders=content-type;host;x-tc-action, "
                + "Signature=760e5cc1a2db3312ffdd42837747d3bb8a4a08ee0b59b2bd27a2f44ba9b55d99", auth);
    }

    @Test
    void mapVoiceType_shouldMapSeriousAndSternTo101004() {
        VoiceServiceImpl service = new VoiceServiceImpl(configured());
        assertEquals("101004", service.mapVoiceType("{\"tone\":\"serious\"}"));
        assertEquals("101004", service.mapVoiceType("{\"tone\":\"stern\"}"));
    }

    @Test
    void mapVoiceType_shouldMapWarmTo101002() {
        VoiceServiceImpl service = new VoiceServiceImpl(configured());
        assertEquals("101002", service.mapVoiceType("{\"tone\":\"warm\"}"));
    }

    @Test
    void mapVoiceType_shouldDefaultTo101001() {
        VoiceServiceImpl service = new VoiceServiceImpl(configured());
        assertEquals("101001", service.mapVoiceType("{\"tone\":\"neutral\"}"));
        assertEquals("101001", service.mapVoiceType("{\"tone\":\"professional\"}"));
        assertEquals("101001", service.mapVoiceType("not valid json"));
        assertEquals("101001", service.mapVoiceType(""));
        assertEquals("101001", service.mapVoiceType(null));
    }

    @Test
    @SuppressWarnings("unchecked")
    void recognizeSpeech_shouldReturnRecognizedText_whenApiSucceeds() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        HttpResponse<String> response = mock(HttpResponse.class);
        doReturn(response).when(httpClient).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("""
                {"Response": {"Result": "你好，我是应聘者", "RequestId": "r-1"}}
                """);

        VoiceService service = new VoiceServiceImpl(configured(), httpClient);
        String text = service.recognizeSpeech(new byte[]{1, 2, 3, 4});
        assertEquals("你好，我是应聘者", text);
    }

    @Test
    @SuppressWarnings("unchecked")
    void recognizeSpeech_shouldThrowBusinessException_whenApiReturnsError() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        HttpResponse<String> response = mock(HttpResponse.class);
        doReturn(response).when(httpClient).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("""
                {"Response": {"Error": {"Code": "InvalidParameter", "Message": "音频数据无效"}, "RequestId": "r-2"}}
                """);

        VoiceService service = new VoiceServiceImpl(configured(), httpClient);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.recognizeSpeech(new byte[]{1, 2, 3}));
        assertTrue(ex.getMessage().contains("音频数据无效"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void synthesizeSpeech_shouldDecodeAudioBytes_whenApiSucceeds() throws Exception {
        byte[] expectedAudio = new byte[]{0, 1, 2, 3, 4, 5};
        String audioBase64 = Base64.getEncoder().encodeToString(expectedAudio);

        HttpClient httpClient = mock(HttpClient.class);
        HttpResponse<String> response = mock(HttpResponse.class);
        doReturn(response).when(httpClient).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("""
                {"Response": {"Audio": "%s", "RequestId": "r-3"}}
                """.formatted(audioBase64));

        VoiceService service = new VoiceServiceImpl(configured(), httpClient);
        byte[] audio = service.synthesizeSpeech("你好，欢迎参加面试", "{\"tone\":\"warm\"}");
        assertArrayEquals(expectedAudio, audio);
    }

    @Test
    @SuppressWarnings("unchecked")
    void synthesizeSpeech_shouldThrowBusinessException_whenAudioMissing() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        HttpResponse<String> response = mock(HttpResponse.class);
        doReturn(response).when(httpClient).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("""
                {"Response": {"RequestId": "r-4"}}
                """);

        VoiceService service = new VoiceServiceImpl(configured(), httpClient);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.synthesizeSpeech("你好", "{\"tone\":\"neutral\"}"));
        assertTrue(ex.getMessage().contains("未包含音频数据"));
    }
}
