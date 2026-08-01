package com.fakemianshi.dto;

import lombok.Data;

/**
 * 考试中的笔试题（脱敏版）：
 * 不包含 answer / explanation，避免考试阶段泄露参考答案。
 */
@Data
public class ExamQuestion {

    private Long id;

    /** 题型：SINGLE_CHOICE / MULTIPLE_CHOICE / FILL_BLANK / SHORT_ANSWER */
    private String type;

    /** 题目内容 */
    private String content;

    /** 选项（JSON 数组字符串，仅选择题使用） */
    private String options;

    /** 题目顺序 */
    private Integer orderNum;
}
