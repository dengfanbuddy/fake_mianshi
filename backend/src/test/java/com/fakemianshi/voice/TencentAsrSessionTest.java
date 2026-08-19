package com.fakemianshi.voice;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TencentAsrSessionTest {

    @Test
    void buildUrlContainsRequiredParamsAndSignature() {
        String url = TencentAsrSession.buildUrl("secretId", "secretKey", "1234567890");
        assertTrue(url.startsWith("wss://asr.cloud.tencent.com/asr/v2/1234567890?"));
        assertTrue(url.contains("secretid=secretId"));
        assertTrue(url.contains("engine_model_type=16k_zh"));
        assertTrue(url.contains("voice_format=1"));
        assertTrue(url.contains("voice_id="));
        assertTrue(url.contains("&signature="));
    }

    @Test
    void base64HmacSha1MatchesKnownVector() {
        // RFC 2202 测试向量：key="key", data="The quick brown fox jumps over the lazy dog"
        String sig = TencentAsrSession.base64HmacSha1("key", "The quick brown fox jumps over the lazy dog");
        assertEquals("3nybhbi3iqa8ino29wqQcBydtNk=", sig);
    }
}
