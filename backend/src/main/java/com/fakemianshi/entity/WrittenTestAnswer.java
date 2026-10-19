package com.fakemianshi.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

/**
 * 笔试作答：用户对一道笔试题的作答及判分结果。
 */
@Data
@TableName("written_test_answer")
public class WrittenTestAnswer {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联的笔试题 ID */
    @TableField("question_id")
    private Long questionId;

    /** 所属面试会话 ID */
    @TableField("session_id")
    private Long sessionId;

    /** 用户作答内容 */
    @TableField("user_answer")
    private String userAnswer;

    /** 是否正确 */
    @TableField("is_correct")
    private Boolean isCorrect;

    /** 得分 */
    private Double score;

    /**
     * 订正时间（ISO-8601 字符串）。NULL 表示这道错题还没订正过。
     *
     * <p>用字符串而不是 DATETIME：SQLite 的日期映射在 MyBatis-Plus + Jackson 3 组合下
     * 容易出现时区/格式差异，而这里只需要「有没有订正过 + 什么时候」，存 ISO 字符串最省事。
     */
    @TableField("corrected_at")
    private String correctedAt;

    /** 重练次数（每次提交重练答案 +1，无论对错） */
    @TableField("retry_count")
    private Integer retryCount;
}
