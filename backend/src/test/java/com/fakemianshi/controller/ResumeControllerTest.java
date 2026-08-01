package com.fakemianshi.controller;

import com.fakemianshi.dto.LlmResponse;
import com.fakemianshi.entity.InterviewProject;
import com.fakemianshi.repository.InterviewProjectRepository;
import com.fakemianshi.service.LlmService;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.io.ByteArrayOutputStream;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 简历上传解析集成测试。
 *
 * <p>测试上传目录指向 target/test-uploads，避免污染真实 uploads 目录；
 * LLM 用 @MockitoBean 替换，保证 analyze 测试不发真实网络请求。
 */
@SpringBootTest(properties = "app.upload-dir=target/test-uploads")
@AutoConfigureMockMvc
@Transactional
class ResumeControllerTest {

    /** 服务器 context-path 为 /api */
    private static final String BASE = ""; // MockMvc 不自动应用 context-path，路径不含 /api

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private InterviewProjectRepository projectRepository;

    @MockitoBean
    private LlmService llmService;

    @Test
    void uploadAndParse_shouldExtractPdfText() throws Exception {
        Long projectId = createProject();
        byte[] pdf = createPdf("Java Senior Engineer with 5 years experience");

        mockMvc.perform(multipart(BASE + "/resume/upload/{projectId}", projectId)
                        .file(new MockMultipartFile("file", "resume.pdf", "application/pdf", pdf)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.projectId").value(projectId))
                .andExpect(jsonPath("$.data.originalFilename").value("resume.pdf"))
                .andExpect(jsonPath("$.data.parsedText")
                        .value(Matchers.containsString("Java Senior Engineer")));
    }

    @Test
    void uploadAndParse_shouldRejectNonPdf() throws Exception {
        Long projectId = createProject();

        mockMvc.perform(multipart(BASE + "/resume/upload/{projectId}", projectId)
                        .file(new MockMultipartFile("file", "resume.txt", "text/plain",
                                "not a pdf".getBytes())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("仅支持 PDF 格式的简历文件"));
    }

    @Test
    void uploadAndParse_shouldRejectEmptyFile() throws Exception {
        Long projectId = createProject();

        mockMvc.perform(multipart(BASE + "/resume/upload/{projectId}", projectId)
                        .file(new MockMultipartFile("file", "resume.pdf", "application/pdf",
                                new byte[0])))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void getLatest_shouldReturnLatestResume() throws Exception {
        Long projectId = createProject();
        byte[] pdf = createPdf("first version");

        String uploadBody = mockMvc.perform(multipart(BASE + "/resume/upload/{projectId}", projectId)
                        .file(new MockMultipartFile("file", "resume.pdf", "application/pdf", pdf)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        long resumeId = objectMapper.readTree(uploadBody).path("data").path("id").asLong();

        mockMvc.perform(get(BASE + "/resume/{projectId}", projectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").value(resumeId))
                .andExpect(jsonPath("$.data.originalFilename").value("resume.pdf"));
    }

    @Test
    void analyze_shouldStoreLlmResult() throws Exception {
        String llmJson = """
                {"experienceYears": 5, "currentPosition": "Java工程师",
                 "techStack": ["Java", "Spring"], "projects": ["订单系统"],
                 "education": "本科", "suggestedPosition": "Java高级工程师",
                 "suggestedSeniority": "高级"}
                """;
        when(llmService.chat(anyString(), anyString()))
                .thenReturn(new LlmResponse(llmJson, "stop", 42));

        Long projectId = createProject();
        byte[] pdf = createPdf("resume content");
        String uploadBody = mockMvc.perform(multipart(BASE + "/resume/upload/{projectId}", projectId)
                        .file(new MockMultipartFile("file", "resume.pdf", "application/pdf", pdf)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        long resumeId = objectMapper.readTree(uploadBody).path("data").path("id").asLong();

        mockMvc.perform(post(BASE + "/resume/analyze/{resumeId}", resumeId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.analysisResult", Matchers.containsString("suggestedPosition")));
    }

    @Test
    void analyze_shouldReturn404_whenResumeNotFound() throws Exception {
        mockMvc.perform(post(BASE + "/resume/analyze/{resumeId}", 999999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404));
    }

    private Long createProject() {
        InterviewProject project = new InterviewProject();
        project.setName("简历测试项目");
        return projectRepository.save(project).getId();
    }

    /** 用 pdfbox 生成一个含指定文本的 PDF 字节流 */
    private byte[] createPdf(String text) throws Exception {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
                contentStream.beginText();
                contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                contentStream.newLineAtOffset(72, 720);
                contentStream.showText(text);
                contentStream.endText();
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        }
    }
}
