package com.fakemianshi.service;

import com.fakemianshi.entity.Resume;
import org.springframework.web.multipart.MultipartFile;

/**
 * 简历服务：简历上传解析、查询与分析。
 */
public interface ResumeService {

    /**
     * 上传并解析简历：保存 PDF 到上传目录并提取文本。
     */
    Resume uploadAndParse(Long projectId, MultipartFile file);

    /**
     * 获取某项目下最新一条简历，没有则返回 null。
     */
    Resume getLatestByProjectId(Long projectId);

    /**
     * 调用 LLM 分析简历，将原始结构化结果存入 analysisResult。
     */
    Resume analyze(Long resumeId);
}
