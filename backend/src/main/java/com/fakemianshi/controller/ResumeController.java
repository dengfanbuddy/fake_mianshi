package com.fakemianshi.controller;

import com.fakemianshi.dto.ApiResponse;
import com.fakemianshi.entity.Resume;
import com.fakemianshi.service.ResumeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 简历接口。
 *
 * <p>注意：服务器 context-path 为 /api，因此这里的 /resume 实际暴露为 /api/resume。
 */
@RestController
@RequestMapping("/resume")
@RequiredArgsConstructor
public class ResumeController {

    private final ResumeService resumeService;

    /** 上传并解析简历 PDF */
    @PostMapping("/upload/{projectId}")
    public ApiResponse<Resume> upload(@PathVariable Long projectId,
                                      @RequestParam("file") MultipartFile file) {
        return ApiResponse.success(resumeService.uploadAndParse(projectId, file));
    }

    /** 获取某项目下最新简历 */
    @GetMapping("/{projectId}")
    public ApiResponse<Resume> getLatest(@PathVariable Long projectId) {
        return ApiResponse.success(resumeService.getLatestByProjectId(projectId));
    }

    /** 调用 LLM 分析简历 */
    @PostMapping("/analyze/{resumeId}")
    public ApiResponse<Resume> analyze(@PathVariable Long resumeId) {
        return ApiResponse.success(resumeService.analyze(resumeId));
    }
}
