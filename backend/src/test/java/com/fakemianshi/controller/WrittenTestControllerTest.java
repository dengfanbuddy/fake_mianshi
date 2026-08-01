package com.fakemianshi.controller;

import com.fakemianshi.entity.InterviewProject;
import com.fakemianshi.entity.InterviewSession;
import com.fakemianshi.entity.WrittenTestQuestion;
import com.fakemianshi.repository.InterviewProjectRepository;
import com.fakemianshi.repository.InterviewSessionRepository;
import com.fakemianshi.repository.WrittenTestAnswerRepository;
import com.fakemianshi.repository.WrittenTestQuestionRepository;
import com.fakemianshi.service.QuestionGenerationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 笔试模块集成测试。
 *
 * <p>使用 @Transactional 让每个测试方法结束后回滚，避免污染真实 SQLite 数据库。
 * QuestionGenerationService 用 @MockitoBean 替换：mock 返回固定题目并真实落库，
 * 避免任何真实 LLM 网络请求，同时保证提交阶段能按 sessionId 查到题目。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class WrittenTestControllerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private InterviewProjectRepository projectRepository;
    @Autowired
    private InterviewSessionRepository sessionRepository;
    @Autowired
    private WrittenTestQuestionRepository questionRepository;
    @Autowired
    private WrittenTestAnswerRepository answerRepository;

    @MockitoBean
    private QuestionGenerationService questionGenerationService;

    private Long projectId;

    @BeforeEach
    void setUp() {
        InterviewProject project = new InterviewProject();
        project.setName("笔试测试项目");
        projectRepository.save(project);
        projectId = project.getId();

        when(questionGenerationService.generateWrittenTestQuestions(anyLong(), anyLong(), anyInt()))
                .thenAnswer(invocation -> {
                    Long sessionId = invocation.getArgument(0);
                    return persistQuestions(fixedQuestions(), sessionId);
                });
    }

    /** 固定题目：覆盖全部题型，便于逐题校验评分规则 */
    private List<WrittenTestQuestion> fixedQuestions() {
        List<WrittenTestQuestion> questions = new ArrayList<>();
        questions.add(q("SINGLE_CHOICE", "单选：以下哪个是并发关键字？",
                "[\"synchronized\",\"volatile\",\"static\",\"final\"]", "A", "synchronized 是并发关键字"));
        questions.add(q("SINGLE_CHOICE", "单选：以下哪个不是并发关键字？",
                "[\"synchronized\",\"volatile\",\"static\",\"final\"]", "B", "static 不是并发关键字"));
        questions.add(q("MULTIPLE_CHOICE", "多选：以下哪些是并发关键字？",
                "[\"synchronized\",\"volatile\",\"static\",\"final\"]", "AB", "synchronized/volatile 是并发关键字"));
        questions.add(q("MULTIPLE_CHOICE", "多选：JVM 运行时数据区包含以下哪些？",
                "[\"堆\",\"虚拟机栈\",\"方法区\",\"注册表\"]", "ABC", "注册表不在运行时数据区"));
        questions.add(q("FILL_BLANK", "Spring 的核心容器接口是 ____",
                null, "ApplicationContext", "IOC 容器核心接口"));
        questions.add(q("SHORT_ANSWER", "简述 HashMap 的扩容机制",
                null, "参考答案", "考察集合知识"));
        return questions;
    }

    private WrittenTestQuestion q(String type, String content, String options, String answer, String explanation) {
        WrittenTestQuestion question = new WrittenTestQuestion();
        question.setType(type);
        question.setContent(content);
        question.setOptions(options);
        question.setAnswer(answer);
        question.setExplanation(explanation);
        question.setOrderNum(1);
        return question;
    }

    /** 给固定题目重新生成 id 并落库，返回带真实 id 的列表 */
    private List<WrittenTestQuestion> persistQuestions(List<WrittenTestQuestion> questions, Long sessionId) {
        List<WrittenTestQuestion> saved = new ArrayList<>();
        int orderNum = 1;
        for (WrittenTestQuestion question : questions) {
            question.setId(null);
            question.setSessionId(sessionId);
            question.setOrderNum(orderNum++);
            saved.add(questionRepository.save(question));
        }
        return saved;
    }

    // ---------- start ----------

    @Test
    void start_shouldReturnQuestionsWithoutAnswers() throws Exception {
        String body = mockMvc.perform(post("/written-test/start/{projectId}", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"timeLimit\":45,\"questionCount\":6}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.sessionId").isNumber())
                .andExpect(jsonPath("$.data.timeLimit").value(45))
                .andExpect(jsonPath("$.data.questions").isArray())
                .andExpect(jsonPath("$.data.questions.length()").value(6))
                .andExpect(jsonPath("$.data.questions[0].id").isNumber())
                .andExpect(jsonPath("$.data.questions[0].type").value("SINGLE_CHOICE"))
                .andExpect(jsonPath("$.data.questions[0].content").isString())
                .andExpect(jsonPath("$.data.questions[0].options").isString())
                .andExpect(jsonPath("$.data.questions[0].orderNum").value(1))
                // 考试阶段不得泄露答案与解析
                .andExpect(jsonPath("$.data.questions[0].answer").doesNotExist())
                .andExpect(jsonPath("$.data.questions[0].explanation").doesNotExist())
                .andExpect(jsonPath("$.data.questions[5].answer").doesNotExist())
                .andExpect(jsonPath("$.data.questions[5].explanation").doesNotExist())
                .andReturn().getResponse().getContentAsString();

        Long sessionId = objectMapper.readTree(body).path("data").path("sessionId").asLong();
        InterviewSession session = sessionRepository.findById(sessionId).orElseThrow();
        assertThat(session.getType()).isEqualTo("WRITTEN");
        assertThat(session.getStatus()).isEqualTo("IN_PROGRESS");
        assertThat(session.getTimeLimit()).isEqualTo(45);
        assertThat(session.getStartedAt()).isNotNull();
    }

    @Test
    void start_shouldApplyDefaults() throws Exception {
        String body = mockMvc.perform(post("/written-test/start/{projectId}", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.timeLimit").value(60))
                .andReturn().getResponse().getContentAsString();

        Long sessionId = objectMapper.readTree(body).path("data").path("sessionId").asLong();
        assertThat(sessionRepository.findById(sessionId).orElseThrow().getTimeLimit()).isEqualTo(60);
        verify(questionGenerationService).generateWrittenTestQuestions(anyLong(), anyLong(), eq(12));
    }

    // ---------- submit ----------

    @Test
    void submit_shouldGradeAllQuestionTypesAndComputeTotalScore() throws Exception {
        StartData data = startTest();

        mockMvc.perform(post("/written-test/submit/{sessionId}", data.sessionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(buildAnswersJson(data.questionIds)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.sessionId").value(data.sessionId))
                .andExpect(jsonPath("$.data.totalQuestions").value(6))
                .andExpect(jsonPath("$.data.correctCount").value(3))
                .andExpect(jsonPath("$.data.totalScore").value(42.1))
                // Q1 单选对
                .andExpect(jsonPath("$.data.results[0].type").value("SINGLE_CHOICE"))
                .andExpect(jsonPath("$.data.results[0].isCorrect").value(true))
                .andExpect(jsonPath("$.data.results[0].score").value(3.0))
                .andExpect(jsonPath("$.data.results[0].correctAnswer").value("A"))
                // Q2 单选错
                .andExpect(jsonPath("$.data.results[1].isCorrect").value(false))
                .andExpect(jsonPath("$.data.results[1].score").value(0.0))
                // Q3 多选乱序全对
                .andExpect(jsonPath("$.data.results[2].type").value("MULTIPLE_CHOICE"))
                .andExpect(jsonPath("$.data.results[2].userAnswer").value("BA"))
                .andExpect(jsonPath("$.data.results[2].isCorrect").value(true))
                .andExpect(jsonPath("$.data.results[2].score").value(3.0))
                // Q4 多选漏选错
                .andExpect(jsonPath("$.data.results[3].isCorrect").value(false))
                .andExpect(jsonPath("$.data.results[3].score").value(0.0))
                // Q5 填空判分（忽略首尾空白 + 忽略大小写）
                .andExpect(jsonPath("$.data.results[4].type").value("FILL_BLANK"))
                .andExpect(jsonPath("$.data.results[4].isCorrect").value(true))
                .andExpect(jsonPath("$.data.results[4].score").value(2.0))
                // Q6 简答不自动判分
                .andExpect(jsonPath("$.data.results[5].type").value("SHORT_ANSWER"))
                .andExpect(jsonPath("$.data.results[5].isCorrect").value(nullValue()))
                .andExpect(jsonPath("$.data.results[5].score").value(0.0))
                .andExpect(jsonPath("$.data.results[5].explanation").value("待AI分析"));

        // 会话已结束
        InterviewSession session = sessionRepository.findById(data.sessionId).orElseThrow();
        assertThat(session.getStatus()).isEqualTo("COMPLETED");
        assertThat(session.getCompletedAt()).isNotNull();
        // 6 道题全部落库了作答记录
        assertThat(answerRepository.findBySessionId(data.sessionId)).hasSize(6);
    }

    @Test
    void submit_shouldReject_whenRepeatedSubmission() throws Exception {
        StartData data = startTest();
        String answersJson = buildAnswersJson(data.questionIds);

        mockMvc.perform(post("/written-test/submit/{sessionId}", data.sessionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(answersJson))
                .andExpect(status().isOk());

        mockMvc.perform(post("/written-test/submit/{sessionId}", data.sessionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(answersJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("笔试已提交或不存在"));
    }

    @Test
    void submit_shouldReject_whenSessionNotFound() throws Exception {
        mockMvc.perform(post("/written-test/submit/{sessionId}", 999999L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"answers\":{}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("笔试已提交或不存在"));
    }

    // ---------- detail ----------

    @Test
    void getDetail_shouldReturnFullInfo() throws Exception {
        StartData data = startTest();
        mockMvc.perform(post("/written-test/submit/{sessionId}", data.sessionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(buildAnswersJson(data.questionIds)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/written-test/{sessionId}", data.sessionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.sessionId").value(data.sessionId))
                .andExpect(jsonPath("$.data.results.length()").value(6))
                .andExpect(jsonPath("$.data.totalScore").value(42.1))
                // 考试已结束，可返回答案与解析
                .andExpect(jsonPath("$.data.results[0].correctAnswer").value("A"))
                .andExpect(jsonPath("$.data.results[0].userAnswer").value("A"))
                .andExpect(jsonPath("$.data.results[0].isCorrect").value(true))
                .andExpect(jsonPath("$.data.results[0].score").value(3.0))
                .andExpect(jsonPath("$.data.results[0].explanation").value("synchronized 是并发关键字"))
                .andExpect(jsonPath("$.data.results[5].type").value("SHORT_ANSWER"))
                .andExpect(jsonPath("$.data.results[5].isCorrect").value(nullValue()))
                .andExpect(jsonPath("$.data.results[5].explanation").value("考察集合知识"));
    }

    @Test
    void getDetail_shouldReturn404_whenSessionNotFound() throws Exception {
        mockMvc.perform(get("/written-test/{sessionId}", 999999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404));
    }

    // ---------- helpers ----------

    /** 开始一场笔试并返回会话 id 与题目 id 列表 */
    private StartData startTest() throws Exception {
        String body = mockMvc.perform(post("/written-test/start/{projectId}", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"timeLimit\":60,\"questionCount\":6}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn().getResponse().getContentAsString();

        JsonNode data = objectMapper.readTree(body).path("data");
        Long sessionId = data.path("sessionId").asLong();
        List<Long> questionIds = new ArrayList<>();
        data.path("questions").forEach(node -> questionIds.add(node.path("id").asLong()));
        return new StartData(sessionId, questionIds);
    }

    /**
     * 固定作答：Q1 对、Q2 错、Q3 乱序全对、Q4 漏选、Q5 填空（带空白/大小写容错）、Q6 简答。
     * 得分 = 3+0+3+0+2+0 = 8，满分 = 3+3+3+3+2+5 = 19，总分 = 8/19*100 = 42.1
     */
    private String buildAnswersJson(List<Long> questionIds) throws Exception {
        Map<String, String> answers = new LinkedHashMap<>();
        answers.put(String.valueOf(questionIds.get(0)), "A");
        answers.put(String.valueOf(questionIds.get(1)), "A");
        answers.put(String.valueOf(questionIds.get(2)), "BA");
        answers.put(String.valueOf(questionIds.get(3)), "AB");
        answers.put(String.valueOf(questionIds.get(4)), " applicationcontext ");
        answers.put(String.valueOf(questionIds.get(5)), "扩容为两倍");
        return "{\"answers\":" + objectMapper.writeValueAsString(answers) + "}";
    }

    private record StartData(Long sessionId, List<Long> questionIds) {
    }
}
