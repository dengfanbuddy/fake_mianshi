package com.fakemianshi.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

/**
 * 笔试题：面试会话中的一道笔试题。
 */
@Data
@TableName("written_test_question")
public class WrittenTestQuestion {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属面试会话 ID */
    @TableField("session_id")
    private Long sessionId;

    /** 题型：SINGLE_CHOICE / MULTIPLE_CHOICE / FILL_BLANK / SHORT_ANSWER */
    private String type;

    /** 题目内容 */
    private String content;

    /** 选项（JSON 数组，仅选择题使用） */
    private String options;

    /** 参考答案 */
    private String answer;

    /** 答案解析 */
    private String explanation;

    /** 涉及知识点（JSON 数组） */
    @TableField("knowledge_points")
    private String knowledgePoints;

    /** 题目顺序 */
    @TableField("order_num")
    private Integer orderNum;
}
