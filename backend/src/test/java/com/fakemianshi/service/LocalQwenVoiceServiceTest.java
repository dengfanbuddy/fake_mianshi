package com.fakemianshi.service;

import com.fakemianshi.config.VoiceProperties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 本地 Qwen 语音服务的人设映射测试：只验证纯逻辑（人设 styleConfig → 音色 + 风格指令），
 * 不发起任何网络请求。
 */
class LocalQwenVoiceServiceTest {

    private LocalQwenVoiceService service() {
        return new LocalQwenVoiceService(new VoiceProperties());
    }

    @Test
    void resolvePersonaVoice_shouldMapToneToSpeakerAndInstruct() {
        LocalQwenVoiceService.PersonaVoice serious = service()
                .resolvePersonaVoice("{\"tone\":\"serious\",\"speed\":\"medium\"}");
        assertEquals("uncle_fu", serious.speaker());
        assertTrue(serious.instruct().contains("冷静"));

        LocalQwenVoiceService.PersonaVoice warm = service()
                .resolvePersonaVoice("{\"tone\":\"warm\",\"speed\":\"slow\"}");
        assertEquals("serena", warm.speaker());
        assertTrue(warm.instruct().contains("语速偏慢"));

        LocalQwenVoiceService.PersonaVoice stern = service()
                .resolvePersonaVoice("{\"tone\":\"stern\",\"speed\":\"fast\",\"aggressiveness\":0.9}");
        assertEquals("dylan", stern.speaker());
        assertTrue(stern.instruct().contains("语速偏快"));
        assertTrue(stern.instruct().contains("犀利"));
    }

    @Test
    void resolvePersonaVoice_shouldPreferExplicitOverrides() {
        LocalQwenVoiceService.PersonaVoice voice = service().resolvePersonaVoice(
                "{\"tone\":\"warm\",\"qwenVoice\":\"vivian\",\"qwenInstruct\":\"低沉沙哑\",\"qwenLanguage\":\"English\"}");
        assertEquals("vivian", voice.speaker());
        assertEquals("低沉沙哑", voice.instruct());
        assertEquals("English", voice.language());
    }

    @Test
    void resolvePersonaVoice_shouldFallbackOnNullOrBrokenJson() {
        LocalQwenVoiceService.PersonaVoice byNull = service().resolvePersonaVoice(null);
        assertEquals("uncle_fu", byNull.speaker());
        assertEquals("Chinese", byNull.language());

        LocalQwenVoiceService.PersonaVoice byBroken = service().resolvePersonaVoice("{not json");
        assertEquals("uncle_fu", byBroken.speaker());
    }

    @Test
    void resolvePersonaVoice_shouldUseDefaultBaseUrlFromProperties() {
        VoiceProperties props = new VoiceProperties();
        props.getLocal().setBaseUrl("http://127.0.0.1:9100/");
        LocalQwenVoiceService svc = new LocalQwenVoiceService(props);
        // 无 VoiceConfigService 时回落配置；末尾斜杠应被裁掉
        assertEquals("http://127.0.0.1:9100", svc.baseUrl());
    }

    @Test
    void personaVoiceRecord_shouldBeUsableAsValue() {
        LocalQwenVoiceService.PersonaVoice a = service().resolvePersonaVoice("{\"tone\":\"neutral\"}");
        LocalQwenVoiceService.PersonaVoice b = service().resolvePersonaVoice("{\"tone\":\"neutral\"}");
        assertEquals(a, b);
        assertNotEquals(a, service().resolvePersonaVoice("{\"tone\":\"stern\"}"));
    }
}
