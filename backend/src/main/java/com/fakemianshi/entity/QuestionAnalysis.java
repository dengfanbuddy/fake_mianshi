package com.fakemianshi.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

/**
 * 单题分析：面试中单个问题/题目的作答质量评估。
 */
@Data
@TableName("question_analysis")
public class QuestionAnalysis {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属面试会话 ID */
    @TableField("session_id")
    private Long sessionId;

    /** 关联的问题引用 ID（如笔试题 ID 或消息 ID） */
    @TableField("question_ref_id")
    private Long questionRefId;

    /** 问题内容 */
    @TableField("question_content")
    private String questionContent;

    /** 用户作答内容 */
    @TableField("answer_content")
    private String answerContent;

    /** 准确度评分（0-100） */
    private Double accuracy;

    /** 准确度评分依据 */
    @TableField("accuracy_reason")
    private String accuracyReason;

    /** 深度评分（0-100） */
    private Double depth;

    /** 深度评分依据 */
    @TableField("depth_reason")
    private String depthReason;

    /** 清晰度评分（0-100） */
    private Double clarity;

    /** 清晰度评分依据 */
    @TableField("clarity_reason")
    private String clarityReason;

    /** 流畅度评分（0-100） */
    private Double fluency;

    /** 流畅度评分依据 */
    @TableField("fluency_reason")
    private String fluencyReason;

    /** 语气/态度评价 */
    @TableField("tone_evaluation")
    private String toneEvaluation;

    /** 技术分类（如 JVM / 并发 / Spring / 数据库） */
    private String category;

    /** 难度（如 初级/中级/高级） */
    private String difficulty;

    /** 考察方向/能力点（该题考的是什么） */
    @TableField("focus_point")
    private String focusPoint;

    /** 这类题的回答思路 */
    @TableField("answer_approach")
    private String answerApproach;

    /** 优秀回答示例 */
    private String example;

    /** 改进建议 */
    @TableField("improvement_suggestion")
    private String improvementSuggestion;
}
