package com.fakemianshi.controller;

import com.fakemianshi.entity.InterviewProject;
import com.fakemianshi.entity.Resume;
import com.fakemianshi.repository.InterviewProjectRepository;
import com.fakemianshi.repository.ResumeRepository;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 职位需求管理集成测试。
 *
 * <p>from-resume 读取的是已有简历的 analysisResult，不触发 LLM 调用，
 * 但为保险起见仍用 @MockitoBean 替换 LlmService，杜绝真实网络请求。
 */
@SpringBootTest(properties = "app.upload-dir=target/test-uploads")
@AutoConfigureMockMvc
@Transactional
class PositionRequirementControllerTest {

    /** 服务器 context-path 为 /api */
    private static final String BASE = ""; // MockMvc 不自动应用 context-path，路径不含 /api

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private InterviewProjectRepository projectRepository;

    @Autowired
    private ResumeRepository resumeRepository;

    @MockitoBean
    private LlmService llmService;

    @Test
    void get_shouldReturnNullData_whenNone() throws Exception {
        Long projectId = createProject();

        mockMvc.perform(get(BASE + "/position/{projectId}", projectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void saveOrUpdate_shouldCreateThenUpdate() throws Exception {
        Long projectId = createProject();

        String firstBody = mockMvc.perform(post(BASE + "/position/{projectId}", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"jobTitle": "Java工程师", "experience": "3-5年", "source": "MANUAL"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.jobTitle").value("Java工程师"))
                .andReturn().getResponse().getContentAsString();
        long id = objectMapper.readTree(firstBody).path("data").path("id").asLong();

        // 再次保存应更新而非新建
        mockMvc.perform(post(BASE + "/position/{projectId}", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"jobTitle": "高级Java工程师", "experience": "5-8年", "source": "MANUAL"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(id))
                .andExpect(jsonPath("$.data.jobTitle").value("高级Java工程师"))
                .andExpect(jsonPath("$.data.experience").value("5-8年"));

        mockMvc.perform(get(BASE + "/position/{projectId}", projectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(id))
                .andExpect(jsonPath("$.data.jobTitle").value("高级Java工程师"));
    }

    @Test
    void fromResume_shouldCreateFromLatestAnalysis() throws Exception {
        Long projectId = createProject();
        saveResumeWithAnalysis(projectId, "Java高级工程师", "高级");

        mockMvc.perform(post(BASE + "/position/{projectId}/from-resume", projectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.jobTitle").value("Java高级工程师"))
                .andExpect(jsonPath("$.data.seniority").value("高级"))
                .andExpect(jsonPath("$.data.source").value("RESUME"))
                .andExpect(jsonPath("$.data.projectId").value(projectId));
    }

    @Test
    void fromResume_shouldReject_whenNoAnalysis() throws Exception {
        Long projectId = createProject();

        mockMvc.perform(post(BASE + "/position/{projectId}/from-resume", projectId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("请先上传并分析简历"));
    }

    @Test
    void fromResume_shouldNotOverwriteExisting() throws Exception {
        Long projectId = createProject();

        // 先手动保存职位需求
        mockMvc.perform(post(BASE + "/position/{projectId}", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"jobTitle": "手动岗位", "source": "MANUAL"}
                                """))
                .andExpect(status().isOk());

        // 再创建带分析结果的简历，from-resume 应返回已有记录
        saveResumeWithAnalysis(projectId, "简历建议岗位", "中级");

        mockMvc.perform(post(BASE + "/position/{projectId}/from-resume", projectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.jobTitle").value("手动岗位"))
                .andExpect(jsonPath("$.data.source").value("MANUAL"));
    }

    private Long createProject() {
        InterviewProject project = new InterviewProject();
        project.setName("职位需求测试项目");
        return projectRepository.save(project).getId();
    }

    private void saveResumeWithAnalysis(Long projectId, String position, String seniority) {
        Resume resume = new Resume();
        resume.setProjectId(projectId);
        resume.setOriginalFilename("resume.pdf");
        resume.setFilePath("/tmp/resume.pdf");
        resume.setParsedText("some resume text");
        resume.setAnalysisResult("""
                {"experienceYears": 5, "currentPosition": "Java工程师",
                 "techStack": ["Java"], "projects": ["x"],
                 "education": "本科",
                 "suggestedPosition": "%s",
                 "suggestedSeniority": "%s"}
                """.formatted(position, seniority));
        resumeRepository.save(resume);
    }
}
