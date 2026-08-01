package com.fakemianshi.entity;

import jakarta.persistence.*;
import lombok.Data;

/**
 * 单题分析：面试中单个问题/题目的作答质量评估。
 */
@Data
@Entity
@Table(name = "question_analysis")
public class QuestionAnalysis {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 所属面试会话 ID */
    @Column(name = "session_id", nullable = false)
    private Long sessionId;

    /** 关联的问题引用 ID（如笔试题 ID 或消息 ID） */
    @Column(name = "question_ref_id")
    private Long questionRefId;

    /** 问题内容 */
    @Column(name = "question_content", length = 5000)
    private String questionContent;

    /** 用户作答内容 */
    @Column(name = "answer_content", length = 10000)
    private String answerContent;

    /** 准确度评分（0-100） */
    private Double accuracy;

    /** 准确度评分依据 */
    @Column(name = "accuracy_reason", length = 1000)
    private String accuracyReason;

    /** 深度评分（0-100） */
    private Double depth;

    /** 深度评分依据 */
    @Column(name = "depth_reason", length = 1000)
    private String depthReason;

    /** 清晰度评分（0-100） */
    private Double clarity;

    /** 清晰度评分依据 */
    @Column(name = "clarity_reason", length = 1000)
    private String clarityReason;

    /** 流畅度评分（0-100） */
    private Double fluency;

    /** 流畅度评分依据 */
    @Column(name = "fluency_reason", length = 1000)
    private String fluencyReason;

    /** 语气/态度评价 */
    @Column(name = "tone_evaluation")
    private String toneEvaluation;

    /** 技术分类（如 JVM / 并发 / Spring / 数据库） */
    @Column(length = 200)
    private String category;

    /** 难度（如 初级/中级/高级） */
    @Column(length = 100)
    private String difficulty;

    /** 考察方向/能力点（该题考的是什么） */
    @Column(name = "focus_point", length = 1000)
    private String focusPoint;

    /** 这类题的回答思路 */
    @Column(name = "answer_approach", length = 3000)
    private String answerApproach;

    /** 优秀回答示例 */
    @Column(length = 3000)
    private String example;

    /** 改进建议 */
    @Column(name = "improvement_suggestion", length = 5000)
    private String improvementSuggestion;
}
