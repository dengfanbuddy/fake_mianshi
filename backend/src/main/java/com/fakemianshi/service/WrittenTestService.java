package com.fakemianshi.service;

import com.fakemianshi.dto.WrittenTestDetailResponse;
import com.fakemianshi.dto.WrittenTestStartRequest;
import com.fakemianshi.dto.WrittenTestStartResponse;
import com.fakemianshi.dto.WrittenTestSubmitRequest;
import com.fakemianshi.dto.WrittenTestSubmitResponse;

/**
 * 笔试服务：开始笔试、提交判分、查看详情。
 */
public interface WrittenTestService {

    /**
     * 开始笔试：创建会话并生成题目，返回脱敏后的题目列表。
     */
    WrittenTestStartResponse start(Long projectId, WrittenTestStartRequest req);

    /**
     * 流式开始笔试：创建会话，题目生成过程通过 onDelta 推送增量文本（SSE 展示），
     * 完成后返回与 {@link #start} 相同的响应。
     */
    WrittenTestStartResponse streamStart(Long projectId, WrittenTestStartRequest req,
                                         java.util.function.Consumer<String> onDelta);

    /**
     * 提交笔试：逐题判分，汇总总分，将会话标记为已完成。
     */
    WrittenTestSubmitResponse submit(Long sessionId, WrittenTestSubmitRequest req);

    /**
     * 笔试详情：题目 + 用户答案 + 对错（考试已结束，可返回答案与解析）。
     */
    WrittenTestDetailResponse getDetail(Long sessionId);
}
