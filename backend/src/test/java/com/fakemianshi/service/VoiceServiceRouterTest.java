package com.fakemianshi.service;

import com.fakemianshi.config.BusinessException;
import com.fakemianshi.config.TencentCloudProperties;
import com.fakemianshi.config.VoiceProperties;
import com.fakemianshi.entity.VoiceConfig;
import com.fakemianshi.service.impl.VoiceServiceImpl;
import com.fakemianshi.service.impl.VoiceServiceRouter;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 供应商路由测试：验证识别请求按当前生效配置分发，以及本地失败时的回退与报错策略。
 *
 * <p>这一层是「本地 ASR 接入后不影响腾讯云路径」的唯一保障，所以两条边都要测：
 * 本地供应商不能偷偷用到腾讯云密钥，腾讯云供应商不能因为本地开关被改道。
 */
class VoiceServiceRouterTest {

    private final VoiceServiceImpl tencent = mock(VoiceServiceImpl.class);
    private final LocalQwenVoiceService local = mock(LocalQwenVoiceService.class);
    private final VoiceConfigService configService = mock(VoiceConfigService.class);
    private final VoiceProperties properties = new VoiceProperties();
    private final TencentCloudProperties tencentProps = new TencentCloudProperties();

    private VoiceServiceRouter router() {
        return new VoiceServiceRouter(tencent, local, configService, properties, tencentProps);
    }

    private VoiceConfig localConfig() {
        VoiceConfig cfg = new VoiceConfig();
        cfg.setProvider(VoiceConfig.PROVIDER_LOCAL_QWEN);
        cfg.setTtsUrl("http://127.0.0.1:9100");
        cfg.setIsActive(true);
        return cfg;
    }

    private VoiceConfig tencentConfig() {
        VoiceConfig cfg = new VoiceConfig();
        cfg.setProvider(VoiceConfig.PROVIDER_TENCENT);
        cfg.setSecretId("AKID_test");
        cfg.setSecretKey("SECRET_test");
        cfg.setAppId("1300000000");
        cfg.setIsActive(true);
        return cfg;
    }

    @Test
    void recognizeSpeech_shouldUseTencent_whenProviderIsTencent() {
        when(configService.resolveActive()).thenReturn(tencentConfig());
        when(tencent.recognizeSpeech(any())).thenReturn("腾讯云识别结果");

        assertEquals("腾讯云识别结果", router().recognizeSpeech(new byte[]{1, 2, 3}));
        verify(local, never()).recognizeSpeech(any());
    }

    @Test
    void recognizeSpeech_shouldUseLocal_whenProviderIsLocal() {
        when(configService.resolveActive()).thenReturn(localConfig());
        when(local.recognizeSpeech(any())).thenReturn("本地识别结果");

        assertEquals("本地识别结果", router().recognizeSpeech(new byte[]{1, 2, 3}));
        verify(tencent, never()).recognizeSpeech(any());
    }

    @Test
    void recognizeSpeech_shouldStayOnTencent_whenLocalAsrDisabled() {
        when(configService.resolveActive()).thenReturn(localConfig());
        properties.getLocal().getAsr().setEnabled(false);
        when(tencent.recognizeSpeech(any())).thenReturn("腾讯云识别结果");

        assertEquals("腾讯云识别结果", router().recognizeSpeech(new byte[]{1, 2, 3}));
        verify(local, never()).recognizeSpeech(any());
    }

    @Test
    void activeAsrIsLocal_shouldRespectEnableSwitch() {
        when(configService.resolveActive()).thenReturn(localConfig());
        assertTrue(router().activeAsrIsLocal());

        properties.getLocal().getAsr().setEnabled(false);
        assertFalse(router().activeAsrIsLocal());

        when(configService.resolveActive()).thenReturn(tencentConfig());
        assertFalse(router().activeAsrIsLocal());
    }

    @Test
    void recognizeSpeech_shouldFallbackToTencent_whenLocalFailsAndKeysAvailable() {
        when(configService.resolveActive()).thenReturn(localConfig());
        properties.getLocal().setFallbackToTencent(true);
        when(local.recognizeSpeech(any()))
                .thenThrow(new BusinessException("本地语音识别调用失败: Connection refused"));
        when(tencent.recognizeSpeech(any())).thenReturn("回退结果");

        // 本地配置里没密钥，但应用级配置有 → 允许回退
        tencentProps.setSecretId("AKID_env");
        tencentProps.setSecretKey("SECRET_env");

        assertEquals("回退结果", router().recognizeSpeech(new byte[]{1, 2, 3}));
        verify(tencent).recognizeSpeech(any());
    }

    @Test
    void recognizeSpeech_shouldThrowLocalError_whenNoTencentKeysToFallBack() {
        when(configService.resolveActive()).thenReturn(localConfig());
        properties.getLocal().setFallbackToTencent(true);
        when(local.recognizeSpeech(any()))
                .thenThrow(new BusinessException("本地语音识别调用失败: Connection refused"));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> router().recognizeSpeech(new byte[]{1, 2, 3}));
        // 报错要指向本地原因（用户刚配的是本地），而不是"未配置腾讯云密钥"
        assertTrue(ex.getMessage().contains("Connection refused"), ex.getMessage());
        verify(tencent, never()).recognizeSpeech(any());
    }

    @Test
    void recognizeSpeech_shouldNotFallback_whenFallbackDisabled() {
        when(configService.resolveActive()).thenReturn(localConfig());
        properties.getLocal().setFallbackToTencent(false);
        tencentProps.setSecretId("AKID_env");
        tencentProps.setSecretKey("SECRET_env");
        when(local.recognizeSpeech(any())).thenThrow(new BusinessException("本地炸了"));

        assertThrows(BusinessException.class, () -> router().recognizeSpeech(new byte[]{1, 2, 3}));
        verify(tencent, never()).recognizeSpeech(any());
    }

    @Test
    void recognizeSpeech_shouldReportBothFailures_whenFallbackAlsoFails() {
        when(configService.resolveActive()).thenReturn(localConfig());
        properties.getLocal().setFallbackToTencent(true);
        tencentProps.setSecretId("AKID_env");
        tencentProps.setSecretKey("SECRET_env");
        when(local.recognizeSpeech(any())).thenThrow(new BusinessException("本地拒绝连接"));
        when(tencent.recognizeSpeech(any())).thenThrow(new BusinessException("腾讯云超时"));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> router().recognizeSpeech(new byte[]{1, 2, 3}));
        assertTrue(ex.getMessage().contains("本地拒绝连接"), ex.getMessage());
        assertTrue(ex.getMessage().contains("腾讯云超时"), ex.getMessage());
    }
}
