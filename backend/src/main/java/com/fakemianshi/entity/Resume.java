package com.fakemianshi.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 简历：上传的简历文件及其解析/分析结果。
 */
@Data
@Entity
@Table(name = "resume")
public class Resume {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 所属项目 ID */
    @Column(name = "project_id", nullable = false)
    private Long projectId;

    /** 文件存储路径 */
    @Column(name = "file_path", length = 500)
    private String filePath;

    /** 原始文件名 */
    @Column(name = "original_filename", length = 255)
    private String originalFilename;

    /** 解析出的纯文本内容 */
    @Column(length = 50000)
    private String parsedText;

    /** 简历分析结果（JSON） */
    @Column(name = "analysis_result", length = 50000)
    private String analysisResult;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }
}
