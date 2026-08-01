package com.fakemianshi.dto;

import lombok.Data;

/**
 * 开始笔试请求。
 */
@Data
public class WrittenTestStartRequest {

    /** 时长限制（分钟），默认 60 */
    private Integer timeLimit;

    /** 题目数量，默认 12 */
    private Integer questionCount;
}
