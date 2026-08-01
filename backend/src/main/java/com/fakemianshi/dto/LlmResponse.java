package com.fakemianshi.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 大模型调用响应。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LlmResponse {

    /** 模型生成的文本内容 */
    private String content;

    /** 结束原因，如 stop / length */
    private String finishReason;

    /** 本次调用消耗的 token 总数 */
    private Integer totalTokens;
}
