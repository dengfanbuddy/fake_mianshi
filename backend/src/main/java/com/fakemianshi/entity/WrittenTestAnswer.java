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
}
