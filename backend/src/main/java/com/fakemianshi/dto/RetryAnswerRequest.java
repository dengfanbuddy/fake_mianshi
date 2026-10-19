package com.fakemianshi.dto;

import lombok.Data;

/**
 * 错题重练请求。
 */
@Data
public class RetryAnswerRequest {

    /**
     * 重练作答。选择题为选项字母（多选如 "AB"），填空为文本。
     * 简答题无法自动判分，允许为空。
     */
    private String userAnswer;
}
