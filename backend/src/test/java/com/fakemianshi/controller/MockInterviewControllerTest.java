package com.fakemianshi.controller;

import com.fakemianshi.dto.MockInterviewRespondRequest;
import com.fakemianshi.dto.MockInterviewRespondResponse;
import com.fakemianshi.dto.MockInterviewStartRequest;
import com.fakemianshi.dto.MockInterviewStartResponse;
import com.fakemianshi.entity.MockInterviewMessage;
import com.fakemianshi.service.MockInterviewService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 模拟面试模块集成测试。
 *
 * <p>用 @MockitoBean 替换 MockInterviewService，全部端点走真实 MVC 层，
 * 校验 URL 映射与 ApiResponse 返回结构，避免任何真实 LLM 网络请求。
 */
@SpringBootTest
@AutoConfigureMockMvc
class MockInterviewControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MockInterviewService mockInterviewService;

    // ---------- start ----------

    @Test
    void start_shouldReturnResponseStructure() throws Exception {
        when(mockInterviewService.start(eq(1L), any(MockInterviewStartRequest.class)))
                .thenReturn(startResponse());

        mockMvc.perform(post("/mock-interview/start/{projectId}", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"personaId\":1,\"referenceWrittenTest\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.sessionId").value(1))
                .andExpect(jsonPath("$.data.personaId").value(1))
                .andExpect(jsonPath("$.data.personaName").value("技术深挖型"))
                .andExpect(jsonPath("$.data.openingMessage").isString())
                .andExpect(jsonPath("$.data.outline").isString());
    }

    @Test
    void start_withoutBody_shouldAlsoWork() throws Exception {
        when(mockInterviewService.start(eq(1L), any(MockInterviewStartRequest.class)))
                .thenReturn(startResponse());

        mockMvc.perform(post("/mock-interview/start/{projectId}", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    // ---------- respond ----------

    @Test
    void respond_shouldReturnBothMessages() throws Exception {
        when(mockInterviewService.respond(eq(1L), any(MockInterviewRespondRequest.class)))
                .thenReturn(respondResponse());

        mockMvc.perform(post("/mock-interview/respond/{sessionId}", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userText\":\"我的回答\",\"audioPath\":\"/uploads/audio/1.wav\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.userMessage.role").value("CANDIDATE"))
                .andExpect(jsonPath("$.data.userMessage.content").value("我的回答"))
                .andExpect(jsonPath("$.data.aiMessage.role").value("INTERVIEWER"))
                .andExpect(jsonPath("$.data.aiMessage.content").value("追问：请详细说说。"));
    }

    // ---------- conclude ----------

    @Test
    void conclude_shouldReturnInterviewerMessage() throws Exception {
        MockInterviewMessage summary = message(5L, "INTERVIEWER", "【面试结束】整体表现不错，建议加强并发实践。", 1L);
        when(mockInterviewService.concludeInterview(1L)).thenReturn(summary);

        mockMvc.perform(post("/mock-interview/conclude/{sessionId}", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.role").value("INTERVIEWER"))
                .andExpect(jsonPath("$.data.content").value("【面试结束】整体表现不错，建议加强并发实践。"))
                .andExpect(jsonPath("$.data.personaId").value(1));
    }

    // ---------- messages ----------

    @Test
    void messages_shouldReturnList() throws Exception {
        when(mockInterviewService.getMessages(1L))
                .thenReturn(List.of(message(1L, "INTERVIEWER", "你好，我们开始吧。", 1L)));

        mockMvc.perform(get("/mock-interview/messages/{sessionId}", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].role").value("INTERVIEWER"))
                .andExpect(jsonPath("$.data[0].content").value("你好，我们开始吧。"));
    }

    // ---------- switch-persona ----------

    @Test
    void switchPersona_shouldAcceptBodyAndReturnVoid() throws Exception {
        mockMvc.perform(put("/mock-interview/switch-persona/{sessionId}", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newPersonaId\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        verify(mockInterviewService).switchPersona(eq(1L), eq(2L));
    }

    // ---------- helpers ----------

    private MockInterviewStartResponse startResponse() {
        MockInterviewStartResponse resp = new MockInterviewStartResponse();
        resp.setSessionId(1L);
        resp.setPersonaId(1L);
        resp.setPersonaName("技术深挖型");
        resp.setOpeningMessage("你好，我是技术深挖型面试官，我们开始吧。");
        resp.setOutline("{\"sections\":[{\"type\":\"TECHNICAL\",\"topics\":[\"JVM\"]}],\"estimatedQuestions\":10}");
        return resp;
    }

    private MockInterviewRespondResponse respondResponse() {
        MockInterviewRespondResponse resp = new MockInterviewRespondResponse();
        resp.setUserMessage(message(2L, "CANDIDATE", "我的回答", null));
        resp.setAiMessage(message(3L, "INTERVIEWER", "追问：请详细说说。", 1L));
        return resp;
    }

    private MockInterviewMessage message(Long id, String role, String content, Long personaId) {
        MockInterviewMessage m = new MockInterviewMessage();
        m.setId(id);
        m.setSessionId(1L);
        m.setRole(role);
        m.setContent(content);
        m.setPersonaId(personaId);
        m.setCreatedAt(LocalDateTime.now());
        return m;
    }
}
