package com.fakemianshi.dto;

import lombok.Data;

/**
 * 开始模拟面试请求。
 */
@Data
public class MockInterviewStartRequest {

    /** 面试官人设 ID，可空（空则后端取第一个预设人设） */
    private Long personaId;

    /** 是否引用最近一次笔试结果（错误题知识点加入重点考察），默认 false */
    private Boolean referenceWrittenTest = false;
}
