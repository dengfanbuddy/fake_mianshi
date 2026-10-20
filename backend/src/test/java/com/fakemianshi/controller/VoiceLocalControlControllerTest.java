package com.fakemianshi.controller;

import com.fakemianshi.service.LocalVoiceProcessManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 本地语音 sidecar 托管接口测试。
 *
 * <p>{@link LocalVoiceProcessManager} 用 {@code @MockitoBean} 替换：
 * 端到端语义（真的拉起 Python、真的随后端退出）由手工验证覆盖，
 * 这里只锁定 HTTP 契约，避免测试环境去 spawn 进程。
 */
@SpringBootTest(properties = "app.upload-dir=target/test-uploads")
@AutoConfigureMockMvc
class VoiceLocalControlControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private LocalVoiceProcessManager localVoiceProcessManager;

    @Test
    void processStatusIsExposed() throws Exception {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("managed", true);
        status.put("running", true);
        status.put("pid", 4321);
        status.put("autoStart", true);
        when(localVoiceProcessManager.status()).thenReturn(status);

        mockMvc.perform(get("/api/voice/local/process"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.managed").value(true))
                .andExpect(jsonPath("$.data.pid").value(4321))
                .andExpect(jsonPath("$.data.autoStart").value(true));
    }

    @Test
    void startDelegatesToManager() throws Exception {
        Map<String, Object> started = new LinkedHashMap<>();
        started.put("started", true);
        started.put("reason", "spawned");
        started.put("pid", 999);
        when(localVoiceProcessManager.start()).thenReturn(started);

        mockMvc.perform(post("/api/voice/local/start"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.started").value(true))
                .andExpect(jsonPath("$.data.pid").value(999));
    }

    @Test
    void stopDelegatesToManagerAndReportsNotManaged() throws Exception {
        Map<String, Object> stopped = new LinkedHashMap<>();
        stopped.put("stopped", false);
        stopped.put("reason", "not-managed");
        when(localVoiceProcessManager.stop(false)).thenReturn(stopped);

        mockMvc.perform(post("/api/voice/local/stop"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.stopped").value(false))
                .andExpect(jsonPath("$.data.reason").value("not-managed"));
    }

    /** force=true 是「后端崩过、pid 记录丢了还占着显存」的回收入口 */
    @Test
    void stopWithForceFlagReachesManager() throws Exception {
        Map<String, Object> stopped = new LinkedHashMap<>();
        stopped.put("stopped", true);
        stopped.put("freed", true);
        when(localVoiceProcessManager.stop(true)).thenReturn(stopped);

        mockMvc.perform(post("/api/voice/local/stop").param("force", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.stopped").value(true))
                .andExpect(jsonPath("$.data.freed").value(true));
    }
}
