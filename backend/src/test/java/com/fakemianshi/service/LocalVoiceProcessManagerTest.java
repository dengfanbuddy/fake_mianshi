package com.fakemianshi.service;

import com.fakemianshi.config.VoiceProperties;
import com.fakemianshi.entity.VoiceConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link LocalVoiceProcessManager} 的所有权判定测试。
 *
 * <p>核心不变量：只有"自己拉起的"进程才能被结束；外部已在运行的实例既不接管、也不误杀。
 * 这些用例全部用 mock 协作者，不真的 spawn Python。
 */
class LocalVoiceProcessManagerTest {

    private VoiceProperties properties;
    private LocalQwenVoiceService localVoiceService;
    private VoiceConfigService voiceConfigService;
    private LocalVoiceProcessManager manager;

    @BeforeEach
    void setUp() {
        properties = new VoiceProperties();
        properties.getLocal().setAutoStart(false);
        properties.getLocal().setStopOnShutdown(false);
        // 避免读到/写出工程目录下的 data/voice-sidecar.pid
        properties.getLocal().setPidFile("target/unit-test-voice-sidecar.pid");

        localVoiceService = mock(LocalQwenVoiceService.class);
        when(localVoiceService.baseUrl()).thenReturn("http://127.0.0.1:9100");
        when(localVoiceService.health()).thenReturn(Map.of("reachable", false));

        voiceConfigService = mock(VoiceConfigService.class);
        manager = new LocalVoiceProcessManager(properties, localVoiceService, voiceConfigService);
    }

    @Test
    void statusReportsNothingManagedInitially() {
        Map<String, Object> status = manager.status();

        assertFalse((Boolean) status.get("managed"), "初始不应托管任何进程");
        assertFalse((Boolean) status.get("running"), "初始不应有运行中的进程");
        assertNull(status.get("pid"));
        assertFalse((Boolean) status.get("autoStart"));
        assertEquals("http://127.0.0.1:9100", status.get("baseUrl"));
    }

    @Test
    void statusExposesResolvedServiceDirAndPython() {
        Map<String, Object> status = manager.status();

        // 测试运行时 user.dir 是 backend 模块目录，../voice-service 存在
        assertTrue(status.containsKey("serviceDir"));
        assertTrue(status.containsKey("python"));
        assertNotNull(status.get("logs"));
    }

    @Test
    void stopWhenNothingStartedReportsNotManaged() {
        Map<String, Object> result = manager.stop();

        assertFalse((Boolean) result.get("stopped"));
        assertEquals("not-managed", result.get("reason"));
    }

    @Test
    void stopNeverKillsExternallyStartedInstance() {
        // sidecar 在跑，但不是后端拉起的
        when(localVoiceService.health()).thenReturn(Map.of("reachable", true));

        Map<String, Object> result = manager.stop();

        assertFalse((Boolean) result.get("stopped"), "外部实例不允许被结束");
        assertEquals("not-managed", result.get("reason"));
        assertFalse((Boolean) result.get("managed"));
    }

    @Test
    void startDoesNotSpawnWhenSidecarAlreadyRunning() {
        when(localVoiceService.health()).thenReturn(Map.of("reachable", true));

        Map<String, Object> result = manager.start();

        assertFalse((Boolean) result.get("started"), "已在运行时不应重复拉起");
        assertEquals("already-running", result.get("reason"));
        assertFalse((Boolean) manager.status().get("running"), "不应记录为被本进程托管");
    }

    @Test
    void autoStartDoesNothingWhenDisabled() {
        // properties.autoStart = false
        manager.autoStartIfNeeded();

        assertFalse((Boolean) manager.status().get("running"));
    }

    @Test
    void autoStartSkippedWhenProviderIsTencent() {
        properties.getLocal().setAutoStart(true);
        VoiceConfig tencent = new VoiceConfig();
        tencent.setProvider("tencent");
        when(voiceConfigService.resolveActive()).thenReturn(tencent);

        manager.autoStartIfNeeded();

        assertFalse((Boolean) manager.status().get("running"), "腾讯云供应商不应拉起本地 sidecar");
    }

    @Test
    void autoStartIsSkippedSafelyWhenConfigUnavailable() {
        properties.getLocal().setAutoStart(true);
        when(voiceConfigService.resolveActive()).thenThrow(new IllegalStateException("db down"));

        manager.autoStartIfNeeded();

        assertFalse((Boolean) manager.status().get("running"));
    }

    @Test
    void shutdownDoesNothingWhenStopOnShutdownDisabled() {
        properties.getLocal().setStopOnShutdown(false);

        manager.shutdown();

        assertFalse((Boolean) manager.status().get("running"));
    }
}
