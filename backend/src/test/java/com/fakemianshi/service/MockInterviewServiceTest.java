package com.fakemianshi.service;

import com.fakemianshi.config.BusinessException;
import com.fakemianshi.dto.LlmResponse;
import com.fakemianshi.dto.MockInterviewRespondRequest;
import com.fakemianshi.dto.MockInterviewRespondResponse;
import com.fakemianshi.dto.MockInterviewStartRequest;
import com.fakemianshi.dto.MockInterviewStartResponse;
import com.fakemianshi.entity.InterviewProject;
import com.fakemianshi.entity.InterviewSession;
import com.fakemianshi.entity.InterviewerPersona;
import com.fakemianshi.entity.MockInterviewMessage;
import com.fakemianshi.repository.InterviewSessionRepository;
import com.fakemianshi.repository.MockInterviewMessageRepository;
import com.fakemianshi.repository.WeaknessTagRepository;
import com.fakemianshi.repository.WrittenTestAnswerRepository;
import com.fakemianshi.repository.WrittenTestQuestionRepository;
import com.fakemianshi.service.impl.MockInterviewServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 模拟面试服务单元测试：纯 Mockito，不启动 Spring 容器。
 */
@SuppressWarnings("unchecked")
class MockInterviewServiceTest {

    private ProjectService projectService;
    private PersonaService personaService;
    private PositionRequirementService positionRequirementService;
    private ResumeService resumeService;
    private InterviewSessionRepository sessionRepository;
    private MockInterviewMessageRepository messageRepository;
    private WrittenTestAnswerRepository writtenTestAnswerRepository;
    private WrittenTestQuestionRepository writtenTestQuestionRepository;
    private WeaknessTagRepository weaknessTagRepository;
    private QuestionGenerationService questionGenerationService;
    private LlmService llmService;
    private MockInterviewService service;

    /** 模拟内存库：消息与会话 */
    private final List<MockInterviewMessage> messages = new ArrayList<>();
    private final Map<Long, InterviewSession> sessions = new HashMap<>();
    private final AtomicLong sessionIdGen = new AtomicLong(1);

    @BeforeEach
    void setUp() {
        projectService = mock(ProjectService.class);
        personaService = mock(PersonaService.class);
        positionRequirementService = mock(PositionRequirementService.class);
        resumeService = mock(ResumeService.class);
        sessionRepository = mock(InterviewSessionRepository.class);
        messageRepository = mock(MockInterviewMessageRepository.class);
        writtenTestAnswerRepository = mock(WrittenTestAnswerRepository.class);
        writtenTestQuestionRepository = mock(WrittenTestQuestionRepository.class);
        weaknessTagRepository = mock(WeaknessTagRepository.class);
        questionGenerationService = mock(QuestionGenerationService.class);
        llmService = mock(LlmService.class);

        service = new MockInterviewServiceImpl(
                projectService, personaService, positionRequirementService, resumeService,
                sessionRepository, messageRepository, writtenTestAnswerRepository,
                writtenTestQuestionRepository, weaknessTagRepository,
                questionGenerationService, llmService);

        // 预设人设
        InterviewerPersona persona = new InterviewerPersona();
        persona.setId(1L);
        persona.setName("技术深挖型");
        persona.setDescription("不断追问底层原理和实现细节，刨根问底");
        persona.setStyleConfig("{\"tone\":\"serious\",\"speed\":\"medium\",\"aggressiveness\":0.8,\"followupStrategy\":\"deep_dive\"}");
        when(personaService.findAll()).thenReturn(List.of(persona));
        when(personaService.findById(1L)).thenReturn(persona);

        InterviewerPersona persona2 = new InterviewerPersona();
        persona2.setId(2L);
        persona2.setName("温和引导型");
        when(personaService.findById(2L)).thenReturn(persona2);

        when(projectService.findById(1L)).thenReturn(new InterviewProject());

        // 模拟会话落库：save 分配 id 并存入 map，findById 从 map 取
        when(sessionRepository.save(any(InterviewSession.class))).thenAnswer(inv -> {
            InterviewSession s = inv.getArgument(0);
            if (s.getId() == null) {
                s.setId(sessionIdGen.getAndIncrement());
            }
            sessions.put(s.getId(), s);
            return s;
        });
        when(sessionRepository.findById(anyLong()))
                .thenAnswer(inv -> Optional.ofNullable(sessions.get(inv.getArgument(0))));
        when(sessionRepository.findByProjectIdAndTypeAndStatusOrderByCompletedAtDesc(anyLong(), anyString(), anyString()))
                .thenReturn(List.of());

        // 模拟消息落库：save 加入 list，findBySessionId 返回全部
        when(messageRepository.save(any(MockInterviewMessage.class))).thenAnswer(inv -> {
            MockInterviewMessage m = inv.getArgument(0);
            if (m.getId() == null) {
                m.setId((long) (messages.size() + 1));
            }
            messages.add(m);
            return m;
        });
        when(messageRepository.findBySessionIdOrderByCreatedAt(anyLong()))
                .thenAnswer(inv -> new ArrayList<>(messages));

        // 简历 / 弱点上下文为空
        when(resumeService.getLatestByProjectId(anyLong())).thenReturn(null);
        when(weaknessTagRepository.findByProjectId(anyLong())).thenReturn(List.of());

        // 出题引擎
        when(questionGenerationService.generateOpeningMessage(anyLong(), anyLong()))
                .thenReturn("你好，我是技术深挖型面试官，我们开始吧。");
        when(questionGenerationService.generateMockInterviewOutline(anyLong()))
                .thenReturn("{\"sections\":[{\"type\":\"TECHNICAL\",\"topics\":[\"JVM\"]}],\"estimatedQuestions\":10}");

        // LLM 回复
        when(llmService.chat(anyList())).thenReturn(new LlmResponse("请先谈谈你对 JVM 内存模型的理解。", "stop", 100));
    }

    // ---------- start ----------

    @Test
    void start_shouldCreateSessionAndSaveOpeningAndReturnOutline() {
        MockInterviewStartRequest req = new MockInterviewStartRequest();
        req.setPersonaId(1L);

        MockInterviewStartResponse response = service.start(1L, req);

        assertEquals(1L, response.getSessionId());
        assertEquals(1L, response.getPersonaId());
        assertEquals("技术深挖型", response.getPersonaName());
        assertEquals("你好，我是技术深挖型面试官，我们开始吧。", response.getOpeningMessage());
        assertEquals("{\"sections\":[{\"type\":\"TECHNICAL\",\"topics\":[\"JVM\"]}],\"estimatedQuestions\":10}",
                response.getOutline());

        InterviewSession session = sessions.get(1L);
        assertEquals("MOCK", session.getType());
        assertEquals("IN_PROGRESS", session.getStatus());
        assertNotNull(session.getStartedAt());

        // 开场白作为第一条 INTERVIEWER 消息保存
        assertEquals(1, messages.size());
        assertEquals("INTERVIEWER", messages.get(0).getRole());
        assertEquals(1L, messages.get(0).getPersonaId());
        assertEquals("你好，我是技术深挖型面试官，我们开始吧。", messages.get(0).getContent());
    }

    @Test
    void start_withoutPersona_shouldPickFirstPreset() {
        MockInterviewStartResponse response = service.start(1L, new MockInterviewStartRequest());

        assertEquals(1L, response.getPersonaId());
        assertEquals("技术深挖型", response.getPersonaName());
    }

    // ---------- respond ----------

    @Test
    void respond_shouldSaveBothMessagesAndSendFullHistoryToLlm() {
        service.start(1L, new MockInterviewStartRequest());

        MockInterviewRespondRequest req = new MockInterviewRespondRequest();
        req.setUserText("我对 JVM 有一定了解，栈管运行，堆管存储。");
        req.setAudioPath("/uploads/audio/1.wav");
        MockInterviewRespondResponse response = service.respond(1L, req);

        assertEquals("CANDIDATE", response.getUserMessage().getRole());
        assertEquals("我对 JVM 有一定了解，栈管运行，堆管存储。", response.getUserMessage().getContent());
        assertEquals("/uploads/audio/1.wav", response.getUserMessage().getAudioPath());
        assertEquals("INTERVIEWER", response.getAiMessage().getRole());
        assertEquals("请先谈谈你对 JVM 内存模型的理解。", response.getAiMessage().getContent());
        assertEquals(1L, response.getAiMessage().getPersonaId());

        // 开场白 + 候选人 + AI 回复 = 3 条
        assertEquals(3, messages.size());

        // 传给 LLM 的完整对话历史：system + 开场白(assistant) + 候选人(user)
        ArgumentCaptor<List<LlmService.Message>> captor = ArgumentCaptor.forClass(List.class);
        verify(llmService).chat(captor.capture());
        List<LlmService.Message> sent = captor.getValue();
        assertEquals(3, sent.size());
        assertEquals("system", sent.get(0).role());
        assertEquals("assistant", sent.get(1).role());
        assertEquals("user", sent.get(2).role());
        assertEquals("你好，我是技术深挖型面试官，我们开始吧。", sent.get(1).content());
        assertEquals("我对 JVM 有一定了解，栈管运行，堆管存储。", sent.get(2).content());
        // system prompt 包含人设身份、大纲、对话规则
        assertTrue(sent.get(0).content().contains("技术深挖型"));
        assertTrue(sent.get(0).content().contains("JVM"));
        assertTrue(sent.get(0).content().contains("【面试结束】"));
    }

    @Test
    void respond_shouldThrowBusinessException_whenSessionNotFound() {
        MockInterviewRespondRequest req = new MockInterviewRespondRequest();
        req.setUserText("回答");

        assertThrows(BusinessException.class, () -> service.respond(999L, req));
    }

    @Test
    void respond_shouldThrowBusinessException_whenSessionCompleted() {
        service.start(1L, new MockInterviewStartRequest());
        sessions.get(1L).setStatus("COMPLETED");

        MockInterviewRespondRequest req = new MockInterviewRespondRequest();
        req.setUserText("回答");

        assertThrows(BusinessException.class, () -> service.respond(1L, req));
    }

    // ---------- conclude ----------

    @Test
    void conclude_shouldSetSessionCompleted() {
        service.start(1L, new MockInterviewStartRequest());

        MockInterviewMessage summary = service.concludeInterview(1L);

        assertEquals("INTERVIEWER", summary.getRole());
        assertEquals("请先谈谈你对 JVM 内存模型的理解。", summary.getContent());
        assertEquals("COMPLETED", sessions.get(1L).getStatus());
        assertNotNull(sessions.get(1L).getCompletedAt());
    }

    // ---------- getMessages ----------

    @Test
    void getMessages_shouldReturnAllMessagesInOrder() {
        service.start(1L, new MockInterviewStartRequest());

        List<MockInterviewMessage> result = service.getMessages(1L);

        assertEquals(1, result.size());
        assertEquals("INTERVIEWER", result.get(0).getRole());
    }

    // ---------- switchPersona ----------

    @Test
    void switchPersona_shouldInsertSwitchNoteMessage() {
        service.start(1L, new MockInterviewStartRequest());

        service.switchPersona(1L, 2L);

        assertEquals(2, messages.size());
        MockInterviewMessage note = messages.get(1);
        assertEquals("INTERVIEWER", note.getRole());
        assertEquals(2L, note.getPersonaId());
        assertTrue(note.getContent().contains("温和引导型"));
    }

    @Test
    void switchPersona_shouldThrowBusinessException_whenSessionNotFound() {
        assertThrows(BusinessException.class, () -> service.switchPersona(999L, 1L));
    }
}
