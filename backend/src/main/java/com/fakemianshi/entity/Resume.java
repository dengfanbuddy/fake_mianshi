package com.fakemianshi.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 简历：上传的简历文件及其解析/分析结果。
 */
@Data
@TableName("resume")
public class Resume {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属项目 ID */
    @TableField("project_id")
    private Long projectId;

    /** 文件存储路径 */
    @TableField("file_path")
    private String filePath;

    /** 原始文件名 */
    @TableField("original_filename")
    private String originalFilename;

    /** 解析出的纯文本内容 */
    private String parsedText;

    /** 简历分析结果（JSON） */
    @TableField("analysis_result")
    private String analysisResult;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    }
