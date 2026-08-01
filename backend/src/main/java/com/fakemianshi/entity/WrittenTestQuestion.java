package com.fakemianshi.entity;

import jakarta.persistence.*;
import lombok.Data;

/**
 * 笔试题：面试会话中的一道笔试题。
 */
@Data
@Entity
@Table(name = "written_test_question")
public class WrittenTestQuestion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 所属面试会话 ID */
    @Column(name = "session_id", nullable = false)
    private Long sessionId;

    /** 题型：SINGLE_CHOICE / MULTIPLE_CHOICE / FILL_BLANK / SHORT_ANSWER */
    @Column(length = 20)
    private String type;

    /** 题目内容 */
    @Column(length = 5000)
    private String content;

    /** 选项（JSON 数组，仅选择题使用） */
    @Column(length = 10000)
    private String options;

    /** 参考答案 */
    @Column(length = 5000)
    private String answer;

    /** 答案解析 */
    @Column(length = 5000)
    private String explanation;

    /** 涉及知识点（JSON 数组） */
    @Column(name = "knowledge_points", length = 2000)
    private String knowledgePoints;

    /** 题目顺序 */
    @Column(name = "order_num")
    private Integer orderNum;
}
