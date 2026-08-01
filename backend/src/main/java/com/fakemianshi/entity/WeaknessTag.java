package com.fakemianshi.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 薄弱知识点标签：用于追踪用户在哪些知识点上掌握不足。
 */
@Data
@TableName("weakness_tag")
public class WeaknessTag {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属项目 ID */
    @TableField("project_id")
    private Long projectId;

    /** 知识点名称 */
    @TableField("knowledge_point")
    private String knowledgePoint;

    /** 掌握程度：WEAK / FAIR / GOOD / STRONG */
    @TableField("mastery_level")
    private String masteryLevel;

    /** 出现次数 */
    @TableField("occurrence_count")
    private Integer occurrenceCount;

    /** 最近一次关联的面试会话 ID */
    @TableField("last_session_id")
    private Long lastSessionId;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    }
