package com.fakemianshi.service.impl;

import com.fakemianshi.config.BusinessException;
import com.fakemianshi.config.ResourceNotFoundException;
import com.fakemianshi.dto.LlmResponse;
import com.fakemianshi.entity.Resume;
import com.fakemianshi.repository.ResumeRepository;
import com.fakemianshi.service.LlmService;
import com.fakemianshi.service.PromptTemplateService;
import com.fakemianshi.service.ResumeService;
import com.fakemianshi.util.PdfUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * 简历服务实现。
 */
@Service
@RequiredArgsConstructor
public class ResumeServiceImpl implements ResumeService {

    private static final String RESUME_SUB_DIR = "resumes";

    private final ResumeRepository resumeRepository;
    private final LlmService llmService;
    private final PromptTemplateService promptTemplateService;

    /** 上传根目录，取自 app.upload-dir 配置 */
    @Value("${app.upload-dir:./uploads}")
    private String uploadDir;

    @Override
    @Transactional
    public Resume uploadAndParse(Long projectId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("请选择要上传的简历文件");
        }

        String originalFilename = sanitizeFilename(file.getOriginalFilename());
        if (originalFilename == null || originalFilename.isBlank()) {
            originalFilename = "resume.pdf";
        }
        String extension = originalFilename.substring(originalFilename.lastIndexOf('.') + 1);
        if (!"pdf".equalsIgnoreCase(extension)) {
            throw new BusinessException("仅支持 PDF 格式的简历文件");
        }

        try {
            Path dir = Paths.get(uploadDir, RESUME_SUB_DIR).toAbsolutePath().normalize();
            Files.createDirectories(dir);
            String storedName = System.currentTimeMillis() + "_" + originalFilename;
            Path target = dir.resolve(storedName).normalize();
            file.transferTo(target.toFile());

            Resume resume = new Resume();
            resume.setProjectId(projectId);
            resume.setFilePath(target.toString());
            resume.setOriginalFilename(originalFilename);
            resume.setParsedText(PdfUtil.extractText(target.toFile()));
            resumeRepository.insert(resume);
            return resume;
        } catch (IOException e) {
            throw new BusinessException("简历文件保存失败: " + e.getMessage(), e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Resume getLatestByProjectId(Long projectId) {
        List<Resume> resumes = resumeRepository.findByProjectId(projectId);
        return resumes.stream()
                .max(Comparator.comparing(Resume::getId))
                .orElse(null);
    }

    @Override
    @Transactional
    public Resume analyze(Long resumeId) {
        Resume resume = Optional.ofNullable(resumeRepository.selectById(resumeId))
                .orElseThrow(() -> new ResourceNotFoundException("简历不存在: id=" + resumeId));

        // 简历分析走提示词模板体系（可自定义）；分析阶段岗位未知，用默认模板
        String systemPrompt = promptTemplateService.getTemplate(null,
                PromptTemplateService.Scene.RESUME_ANALYSIS.name(), "");

        LlmResponse response = llmService.chat(systemPrompt, resume.getParsedText());
        resume.setAnalysisResult(response.getContent());
        resumeRepository.updateById(resume);
        return resume;
    }

    /** 去除文件名中的路径分隔符，防止路径穿越 */
    private String sanitizeFilename(String filename) {
        if (filename == null) {
            return null;
        }
        return filename.replace('\\', '_').replace('/', '_');
    }
}
