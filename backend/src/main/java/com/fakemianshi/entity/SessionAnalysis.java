package com.fakemianshi.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 会话整体分析：一次面试会话结束后的综合评估报告。
 */
@Data
@TableName("session_analysis")
public class SessionAnalysis {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属面试会话 ID */
    @TableField("session_id")
    private Long sessionId;

    /** 综合得分 */
    @TableField("overall_score")
    private Double overallScore;

    /** 优势（JSON 数组） */
    private String strengths;

    /** 不足（JSON 数组） */
    private String weaknesses;

    /** 知识盲区（JSON 数组，元素为 {"point":"知识点","explanation":"答案要点"}） */
    @TableField("knowledge_gaps")
    private String knowledgeGaps;

    /** 能力等级总结（如 初级/中级/高级/专家级） */
    @TableField("overall_level")
    private String overallLevel;

    /** 预期薪资范围（如 20k-30k） */
    @TableField("expected_salary_range")
    private String expectedSalaryRange;

    /** 性格总结 */
    @TableField("personality_summary")
    private String personalitySummary;

    /** 性格特质（JSON 数组） */
    @TableField("character_traits")
    private String characterTraits;

    /** 性格缺陷与改进（JSON 数组，元素为 {"defect":"...","improvement":"..."}） */
    @TableField("character_defects")
    private String characterDefects;

    /** 沟通表达评估 */
    @TableField("communication_evaluation")
    private String communicationEvaluation;

    /** 幽默化总结（AI 根据能力水平给出的善意调侃/夸赞） */
    @TableField("humor_summary")
    private String humorSummary;

    /** 改进计划（JSON） */
    @TableField("improvement_plan")
    private String improvementPlan;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    }
