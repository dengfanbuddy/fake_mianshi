package com.fakemianshi.service;

import com.fakemianshi.config.BusinessException;
import com.fakemianshi.config.TencentCloudProperties;
import com.fakemianshi.config.VoiceProperties;
import com.fakemianshi.dto.VoiceConfigDTO;
import com.fakemianshi.entity.VoiceConfig;
import com.fakemianshi.repository.VoiceConfigRepository;
import com.fakemianshi.service.impl.VoiceConfigServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 识别热词的配置语义测试：这是唯一一处「页面留空」不等于「清空」的配置项，容易理解错。
 *
 * <p>约定：页面留空 = 用内置默认表（不是不要热词）。原因是内置表有实测收益
 * （不加时 "JVM" 会被识别成 "JYEM"），真清空属于反直觉且有害的操作。
 */
class VoiceConfigAsrPromptTest {

    private static final String BUILT_IN = "术语：JVM、GC、Redis";

    private final VoiceConfigRepository repository = mock(VoiceConfigRepository.class);
    private final TencentCloudProperties tencentProps = new TencentCloudProperties();
    private final VoiceProperties voiceProperties = new VoiceProperties();

    private VoiceConfigServiceImpl service() {
        voiceProperties.getLocal().getAsr().setPrompt(BUILT_IN);
        return new VoiceConfigServiceImpl(repository, tencentProps, voiceProperties);
    }

    private VoiceConfig activeLocal(String asrPrompt) {
        VoiceConfig cfg = new VoiceConfig();
        cfg.setProvider(VoiceConfig.PROVIDER_LOCAL_QWEN);
        cfg.setTtsUrl("http://127.0.0.1:9100");
        cfg.setIsActive(true);
        cfg.setAsrPrompt(asrPrompt);
        return cfg;
    }

    private VoiceConfigDTO dto(String asrPrompt) {
        VoiceConfigDTO dto = new VoiceConfigDTO();
        dto.setProvider(VoiceConfig.PROVIDER_LOCAL_QWEN);
        dto.setTtsUrl("http://127.0.0.1:9100");
        dto.setAsrPrompt(asrPrompt);
        return dto;
    }

    @Test
    void saveConfig_shouldStoreTrimmedPrompt() {
        when(repository.findByIsActiveTrue()).thenReturn(Optional.empty());
        VoiceConfig saved = service().saveConfig(dto("  术语：Kubernetes、分库分表  "));
        assertEquals("术语：Kubernetes、分库分表", saved.getAsrPrompt());
        verify(repository).insert(any(VoiceConfig.class));
    }

    @Test
    void saveConfig_shouldTreatBlankAsNull_soBuiltInDefaultApplies() {
        when(repository.findByIsActiveTrue()).thenReturn(Optional.empty());
        assertNull(service().saveConfig(dto("   ")).getAsrPrompt());
        assertNull(service().saveConfig(dto(null)).getAsrPrompt());
    }

    @Test
    void saveConfig_shouldRejectOverlongPrompt() {
        when(repository.findByIsActiveTrue()).thenReturn(Optional.empty());
        String tooLong = "术".repeat(501);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service().saveConfig(dto(tooLong)));
        // 报错要说清"为什么"而不只是"太长"，否则用户会以为可以调大限制
        assertEquals(true, ex.getMessage().contains("500"), ex.getMessage());
        assertEquals(true, ex.getMessage().contains("稀释"), ex.getMessage());
    }

    @Test
    void effectiveAsrPrompt_shouldPreferPageValue() {
        when(repository.findByIsActiveTrue()).thenReturn(Optional.of(activeLocal("术语：我的技术栈")));
        assertEquals("术语：我的技术栈", service().effectiveAsrPrompt());
    }

    @Test
    void effectiveAsrPrompt_shouldFallBackToBuiltIn_whenPageValueBlank() {
        when(repository.findByIsActiveTrue()).thenReturn(Optional.of(activeLocal(null)));
        assertEquals(BUILT_IN, service().effectiveAsrPrompt());

        when(repository.findByIsActiveTrue()).thenReturn(Optional.of(activeLocal("  ")));
        assertEquals(BUILT_IN, service().effectiveAsrPrompt());
    }

    @Test
    void effectiveAsrPrompt_shouldFallBackToBuiltIn_whenNothingConfigured() {
        when(repository.findByIsActiveTrue()).thenReturn(Optional.empty());
        assertEquals(BUILT_IN, service().effectiveAsrPrompt());
    }

    @Test
    void getActiveConfig_shouldExposePromptAndBuiltInDefault() {
        when(repository.findByIsActiveTrue()).thenReturn(Optional.of(activeLocal("术语：我的")));
        VoiceConfigDTO dto = service().getActiveConfig();
        assertEquals("术语：我的", dto.getAsrPrompt());
        // 页面拿默认值做 placeholder / "填入内置默认"，否则用户不知道内置表长什么样
        assertEquals(BUILT_IN, dto.getAsrPromptDefault());
    }

    @Test
    void localService_shouldUsePromptFromPageConfig() {
        when(repository.findByIsActiveTrue()).thenReturn(Optional.of(activeLocal("术语：页面配置的热词")));
        VoiceConfigServiceImpl configService = service();

        LocalQwenVoiceService local = new LocalQwenVoiceService(voiceProperties);
        ReflectionTestUtils.setField(local, "voiceConfigService", configService);
        assertEquals("术语：页面配置的热词", local.effectiveAsrPrompt());
    }

    @Test
    void localService_shouldFallBackToProperties_whenConfigServiceAbsent() {
        LocalQwenVoiceService local = new LocalQwenVoiceService(voiceProperties);
        voiceProperties.getLocal().getAsr().setPrompt(BUILT_IN);
        // 没有注入配置服务时（单测/独立使用）用配置文件里的值
        assertEquals(BUILT_IN, local.effectiveAsrPrompt());
    }
}
