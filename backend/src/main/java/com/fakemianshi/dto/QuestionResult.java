package com.fakemianshi.dto;

import lombok.Data;

/**
 * 单题判分结果。
 */
@Data
public class QuestionResult {

    private Long questionId;

    /** 用户作答内容 */
    private String userAnswer;

    /** 参考答案 */
    private String correctAnswer;

    /** 是否正确；简答题未做自动判分时为 null */
    private Boolean isCorrect;

    /** 本题得分（按题型权重计） */
    private Double score;

    /** 答案解析 */
    private String explanation;

    /** 题型：SINGLE_CHOICE / MULTIPLE_CHOICE / FILL_BLANK / SHORT_ANSWER */
    private String type;
}
