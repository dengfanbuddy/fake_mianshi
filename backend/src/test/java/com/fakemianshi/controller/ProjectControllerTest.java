package com.fakemianshi.controller;

import com.fakemianshi.entity.HistoricalAnalysis;
import com.fakemianshi.entity.InterviewProject;
import com.fakemianshi.entity.InterviewSession;
import com.fakemianshi.entity.MockInterviewMessage;
import com.fakemianshi.entity.PositionRequirement;
import com.fakemianshi.entity.QuestionAnalysis;
import com.fakemianshi.entity.Resume;
import com.fakemianshi.entity.SessionAnalysis;
import com.fakemianshi.entity.WeaknessTag;
import com.fakemianshi.entity.WrittenTestAnswer;
import com.fakemianshi.entity.WrittenTestQuestion;
import com.fakemianshi.repository.HistoricalAnalysisRepository;
import com.fakemianshi.repository.InterviewProjectRepository;
import com.fakemianshi.repository.InterviewSessionRepository;
import com.fakemianshi.repository.MockInterviewMessageRepository;
import com.fakemianshi.repository.PositionRequirementRepository;
import com.fakemianshi.repository.QuestionAnalysisRepository;
import com.fakemianshi.repository.ResumeRepository;
import com.fakemianshi.repository.SessionAnalysisRepository;
import com.fakemianshi.repository.WeaknessTagRepository;
import com.fakemianshi.repository.WrittenTestAnswerRepository;
import com.fakemianshi.repository.WrittenTestQuestionRepository;
import com.fakemianshi.service.LlmService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 面试项目 CRUD 集成测试。
 *
 * <p>使用 @Transactional 让每个测试方法结束后回滚，避免污染真实 SQLite 数据库。
 * LlmService 用 @MockitoBean 替换，防止任何真实网络请求。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ProjectControllerTest {

    /** 服务器 context-path 为 /api */
    private static final String BASE = ""; // MockMvc 不自动应用 context-path，路径不含 /api

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private InterviewProjectRepository projectRepository;
    @Autowired
    private ResumeRepository resumeRepository;
    @Autowired
    private InterviewSessionRepository sessionRepository;
    @Autowired
    private WrittenTestQuestionRepository writtenTestQuestionRepository;
    @Autowired
    private WrittenTestAnswerRepository writtenTestAnswerRepository;
    @Autowired
    private MockInterviewMessageRepository mockInterviewMessageRepository;
    @Autowired
    private SessionAnalysisRepository sessionAnalysisRepository;
    @Autowired
    private QuestionAnalysisRepository questionAnalysisRepository;
    @Autowired
    private WeaknessTagRepository weaknessTagRepository;
    @Autowired
    private HistoricalAnalysisRepository historicalAnalysisRepository;
    @Autowired
    private PositionRequirementRepository positionRequirementRepository;

    @MockitoBean
    private LlmService llmService;

    @Test
    void create_shouldReturnProject() throws Exception {
        mockMvc.perform(post(BASE + "/project")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Java后端面试", "description": "全面准备"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").isNumber())
                .andExpect(jsonPath("$.data.name").value("Java后端面试"))
                .andExpect(jsonPath("$.data.description").value("全面准备"));
    }

    @Test
    void create_shouldRejectBlankName() throws Exception {
        mockMvc.perform(post(BASE + "/project")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "  ", "description": "x"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("项目名称不能为空"));
    }

    @Test
    void list_shouldReturnProjectsInDescendingOrder() throws Exception {
        Long first = createProject("项目A");
        Long second = createProject("项目B");

        mockMvc.perform(get(BASE + "/project"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data[0].id").value(second))
                .andExpect(jsonPath("$.data[1].id").value(first));
    }

    @Test
    void get_shouldReturnProjectById() throws Exception {
        Long id = createProject("查询项目");

        mockMvc.perform(get(BASE + "/project/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.name").value("查询项目"));
    }

    @Test
    void get_shouldReturn404_whenNotFound() throws Exception {
        mockMvc.perform(get(BASE + "/project/{id}", 999999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    void update_shouldModifyNameAndKeepCreatedAt() throws Exception {
        Long id = createProject("原名");
        InterviewProject before = projectRepository.findById(id).orElseThrow();

        mockMvc.perform(put(BASE + "/project/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "新名", "description": "新描述"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("新名"))
                .andExpect(jsonPath("$.data.description").value("新描述"));

        InterviewProject after = projectRepository.findById(id).orElseThrow();
        assertThat(after.getCreatedAt()).isEqualTo(before.getCreatedAt());
    }

    @Test
    void delete_shouldRemoveProjectAndAllRelatedData() throws Exception {
        Long projectId = createProject("待删除");
        Long sessionId = createProjectWithRelatedData(projectId);

        mockMvc.perform(delete(BASE + "/project/{id}", projectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        assertThat(projectRepository.existsById(projectId)).isFalse();
        assertThat(resumeRepository.findByProjectId(projectId)).isEmpty();
        assertThat(sessionRepository.findByProjectIdOrderByCreatedAtDesc(projectId)).isEmpty();
        assertThat(weaknessTagRepository.findByProjectId(projectId)).isEmpty();
        assertThat(historicalAnalysisRepository.findByProjectIdOrderByGeneratedAtDesc(projectId)).isEmpty();
        assertThat(positionRequirementRepository.findByProjectId(projectId)).isEmpty();
        assertThat(writtenTestQuestionRepository.findBySessionIdOrderByOrderNum(sessionId)).isEmpty();
        assertThat(writtenTestAnswerRepository.findBySessionId(sessionId)).isEmpty();
        assertThat(mockInterviewMessageRepository.findBySessionIdOrderByCreatedAt(sessionId)).isEmpty();
        assertThat(sessionAnalysisRepository.findBySessionId(sessionId)).isEmpty();
        assertThat(questionAnalysisRepository.findBySessionId(sessionId)).isEmpty();
    }

    @Test
    void delete_shouldReturn404_whenNotFound() throws Exception {
        mockMvc.perform(delete(BASE + "/project/{id}", 999999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404));
    }

    /** 创建项目并返回其 id */
    private Long createProject(String name) throws Exception {
        String body = mockMvc.perform(post(BASE + "/project")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"" + name + "\", \"description\": \"desc\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data").path("id").asLong();
    }

    /** 构造包含全部关联数据的项目，返回会话 id */
    private Long createProjectWithRelatedData(Long projectId) {
        Resume resume = new Resume();
        resume.setProjectId(projectId);
        resume.setOriginalFilename("resume.pdf");
        resume.setFilePath("/tmp/resume.pdf");
        resume.setParsedText("resume text");
        resumeRepository.save(resume);

        WeaknessTag tag = new WeaknessTag();
        tag.setProjectId(projectId);
        tag.setKnowledgePoint("SQL索引");
        tag.setMasteryLevel("WEAK");
        weaknessTagRepository.save(tag);

        HistoricalAnalysis history = new HistoricalAnalysis();
        history.setProjectId(projectId);
        history.setAnalysisData("{}");
        historicalAnalysisRepository.save(history);

        PositionRequirement pos = new PositionRequirement();
        pos.setProjectId(projectId);
        pos.setJobTitle("Java工程师");
        pos.setSource("MANUAL");
        positionRequirementRepository.save(pos);

        InterviewSession session = new InterviewSession();
        session.setProjectId(projectId);
        session.setType("WRITTEN");
        session.setStatus("COMPLETED");
        sessionRepository.save(session);
        Long sessionId = session.getId();

        WrittenTestQuestion question = new WrittenTestQuestion();
        question.setSessionId(sessionId);
        question.setType("SHORT_ANSWER");
        question.setContent("什么是索引？");
        writtenTestQuestionRepository.save(question);

        WrittenTestAnswer answer = new WrittenTestAnswer();
        answer.setSessionId(sessionId);
        answer.setQuestionId(question.getId());
        answer.setUserAnswer("加速查询");
        writtenTestAnswerRepository.save(answer);

        MockInterviewMessage message = new MockInterviewMessage();
        message.setSessionId(sessionId);
        message.setRole("INTERVIEWER");
        message.setContent("请介绍你自己");
        mockInterviewMessageRepository.save(message);

        SessionAnalysis sessionAnalysis = new SessionAnalysis();
        sessionAnalysis.setSessionId(sessionId);
        sessionAnalysisRepository.save(sessionAnalysis);

        QuestionAnalysis questionAnalysis = new QuestionAnalysis();
        questionAnalysis.setSessionId(sessionId);
        questionAnalysisRepository.save(questionAnalysis);

        return sessionId;
    }
}
