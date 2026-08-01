package com.fakemianshi.service;

import com.fakemianshi.config.BusinessException;
import com.fakemianshi.dto.LlmResponse;
import com.fakemianshi.entity.InterviewerPersona;
import com.fakemianshi.entity.PositionRequirement;
import com.fakemianshi.entity.Resume;
import com.fakemianshi.entity.WeaknessTag;
import com.fakemianshi.entity.WrittenTestQuestion;
import com.fakemianshi.repository.InterviewerPersonaRepository;
import com.fakemianshi.repository.WeaknessTagRepository;
import com.fakemianshi.repository.WrittenTestQuestionRepository;
import com.fakemianshi.service.impl.QuestionGenerationServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 出题引擎单元测试：纯 Mockito，不启动 Spring 容器。
 */
class QuestionGenerationServiceTest {

    private LlmService llmService;
    private ResumeService resumeService;
    private PositionRequirementService positionRequirementService;
    private WeaknessTagRepository weaknessTagRepository;
    private WrittenTestQuestionRepository writtenTestQuestionRepository;
    private InterviewerPersonaRepository interviewerPersonaRepository;
    private QuestionGenerationService service;

    @BeforeEach
    void setUp() {
        llmService = mock(LlmService.class);
        resumeService = mock(ResumeService.class);
        positionRequirementService = mock(PositionRequirementService.class);
        weaknessTagRepository = mock(WeaknessTagRepository.class);
        writtenTestQuestionRepository = mock(WrittenTestQuestionRepository.class);
        interviewerPersonaRepository = mock(InterviewerPersonaRepository.class);

        service = new QuestionGenerationServiceImpl(
                llmService, resumeService, positionRequirementService,
                weaknessTagRepository, writtenTestQuestionRepository,
                interviewerPersonaRepository);

        // save 直接返回传入对象，模拟 JPA 行为
        when(writtenTestQuestionRepository.save(any(WrittenTestQuestion.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    private void stubContext() {
        PositionRequirement req = new PositionRequirement();
        req.setJobTitle("Java开发工程师");
        req.setSeniority("中级");
        req.setTechStack("[\"Java\",\"Spring\",\"MySQL\"]");
        when(positionRequirementService.getByProjectId(1L)).thenReturn(req);

        Resume resume = new Resume();
        resume.setAnalysisResult("{\"techStack\":[\"Java\"],\"suggestedPosition\":\"Java开发\"}");
        when(resumeService.getLatestByProjectId(1L)).thenReturn(resume);

        WeaknessTag tag = new WeaknessTag();
        tag.setKnowledgePoint("JVM");
        tag.setMasteryLevel("WEAK");
        when(weaknessTagRepository.findByProjectId(1L)).thenReturn(List.of(tag));
    }

    private static final String QUESTIONS_JSON = """
            [
              {"type":"SINGLE_CHOICE","content":"JVM运行时数据区包含哪些？","options":["堆","栈","方法区","注册表"],"answer":"D","explanation":"运行时数据区不含注册表","knowledgePoints":["JVM"]},
              {"type":"MULTIPLE_CHOICE","content":"哪些是并发关键字？","options":["synchronized","volatile","static","final"],"answer":"A,B","explanation":"static/final 不是并发关键字","knowledgePoints":["并发"]},
              {"type":"FILL_BLANK","content":"Spring 的 IOC 容器是 ____","options":null,"answer":"ApplicationContext","explanation":"IOC 容器核心接口","knowledgePoints":["Spring"]},
              {"type":"SHORT_ANSWER","content":"简述 HashMap 的扩容机制","options":null,"answer":"...","explanation":"...","knowledgePoints":["集合"]}
            ]
            """;

    @Test
    void generateWrittenTestQuestions_shouldParseAndSaveAllQuestions() {
        stubContext();
        when(llmService.chat(anyString(), anyString()))
                .thenReturn(new LlmResponse(QUESTIONS_JSON, "stop", 100));

        List<WrittenTestQuestion> questions = service.generateWrittenTestQuestions(10L, 1L, 4);

        assertEquals(4, questions.size());
        // type 映射正确
        assertEquals("SINGLE_CHOICE", questions.get(0).getType());
        assertEquals("MULTIPLE_CHOICE", questions.get(1).getType());
        assertEquals("FILL_BLANK", questions.get(2).getType());
        assertEquals("SHORT_ANSWER", questions.get(3).getType());
        // orderNum 递增
        assertEquals(1, questions.get(0).getOrderNum());
        assertEquals(2, questions.get(1).getOrderNum());
        assertEquals(3, questions.get(2).getOrderNum());
        assertEquals(4, questions.get(3).getOrderNum());
        // sessionId 正确
        assertEquals(10L, questions.get(0).getSessionId());
        // 选择题 options 序列化保留，填空/简答 options 为 null
        assertTrue(questions.get(0).getOptions().contains("堆"));
        assertNull(questions.get(2).getOptions());
        assertNull(questions.get(3).getOptions());
        // knowledgePoints 序列化
        assertTrue(questions.get(0).getKnowledgePoints().contains("JVM"));
        // 全部保存到 repository
        verify(writtenTestQuestionRepository, org.mockito.Mockito.times(4))
                .save(any(WrittenTestQuestion.class));
    }

    @Test
    void generateWrittenTestQuestions_shouldHandleMarkdownFence() {
        stubContext();
        String fenced = "以下是生成结果：\n```json\n" + QUESTIONS_JSON + "\n```\n请查收";
        when(llmService.chat(anyString(), anyString()))
                .thenReturn(new LlmResponse(fenced, "stop", 100));

        List<WrittenTestQuestion> questions = service.generateWrittenTestQuestions(10L, 1L, 4);

        assertEquals(4, questions.size());
        assertEquals("SINGLE_CHOICE", questions.get(0).getType());
        assertEquals("FILL_BLANK", questions.get(2).getType());
    }

    @Test
    void generateWrittenTestQuestions_shouldThrowBusinessException_whenJsonInvalid() {
        stubContext();
        when(llmService.chat(anyString(), anyString()))
                .thenReturn(new LlmResponse("这不是 JSON", "stop", 10));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.generateWrittenTestQuestions(10L, 1L, 4));
        assertEquals("题目生成格式异常，请重试", ex.getMessage());
        // 解析失败不保存任何题目
        verify(writtenTestQuestionRepository, never()).save(any(WrittenTestQuestion.class));
    }

    @Test
    void generateWrittenTestQuestions_shouldThrowBusinessException_whenNotArray() {
        stubContext();
        when(llmService.chat(anyString(), anyString()))
                .thenReturn(new LlmResponse("{\"items\":[]}", "stop", 10));

        assertThrows(BusinessException.class,
                () -> service.generateWrittenTestQuestions(10L, 1L, 4));
    }

    @Test
    void generateMockInterviewOutline_shouldReturnLlmRawText() {
        stubContext();
        String outline = "{\"sections\":[{\"type\":\"TECHNICAL\",\"topics\":[\"JVM\"]}],\"estimatedQuestions\":15}";
        when(llmService.chat(anyString(), anyString()))
                .thenReturn(new LlmResponse(outline, "stop", 50));

        String result = service.generateMockInterviewOutline(1L);

        assertEquals(outline, result);
    }

    @Test
    void generateOpeningMessage_shouldReturnLlmRawText() {
        stubContext();
        InterviewerPersona persona = new InterviewerPersona();
        persona.setName("张面试官");
        persona.setDescription("严肃专业");
        when(interviewerPersonaRepository.findById(7L)).thenReturn(Optional.of(persona));
        when(llmService.chat(anyString(), anyString()))
                .thenReturn(new LlmResponse("你好，我是张面试官，我们开始吧。", "stop", 20));

        String result = service.generateOpeningMessage(1L, 7L);

        assertEquals("你好，我是张面试官，我们开始吧。", result);
    }

    @Test
    void generateOpeningMessage_shouldThrow_whenPersonaNotFound() {
        when(interviewerPersonaRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(BusinessException.class,
                () -> service.generateOpeningMessage(1L, 999L));
    }
}
