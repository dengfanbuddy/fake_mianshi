package com.fakemianshi.dto;

import lombok.Data;

import java.util.Map;

/**
 * 提交笔试请求：questionId → 用户答案。
 * 选择题答案为选项字母（如 "A" / "AB"），填空/简答为文本。
 */
@Data
public class WrittenTestSubmitRequest {

    /** questionId → 用户答案 */
    private Map<Long, String> answers;
}
