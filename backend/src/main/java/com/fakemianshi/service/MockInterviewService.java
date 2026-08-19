package com.fakemianshi.service;

import com.fakemianshi.dto.MockInterviewRespondRequest;
import com.fakemianshi.dto.MockInterviewRespondResponse;
import com.fakemianshi.dto.MockInterviewStartRequest;
import com.fakemianshi.dto.MockInterviewStartResponse;
import com.fakemianshi.entity.MockInterviewMessage;

import java.util.List;

/**
 * 模拟面试服务：会话管理与 AI 面试官动态对话生成。
 */
public interface MockInterviewService {

    /**
     * 开始一场模拟面试：创建 MOCK 会话、生成并保存开场白、生成大纲返回前端。
     */
    MockInterviewStartResponse start(Long projectId, MockInterviewStartRequest req);

    /**
     * 候选人回复后，由 AI 面试官实时生成下一问/追问并保存双方消息。
     */
    MockInterviewRespondResponse respond(Long sessionId, MockInterviewRespondRequest req);

    /**
     * 流式版 {@link #respond}：边生成边回调文本增量，供 SSE 打字机效果；返回最终保存的双方消息。
     *
     * @param onDelta     面试官回复文本增量回调（可为 null）
     * @param onReasoning 思考过程增量回调（可为 null）
     */
    MockInterviewRespondResponse respondStreaming(Long sessionId, MockInterviewRespondRequest req,
                                                  java.util.function.Consumer<String> onDelta,
                                                  java.util.function.Consumer<String> onReasoning);

    /**
     * 全链路流式面试回复（Phase B）：识别文本 → 流式 LLM → 按句流式 TTS，
     * 文本增量经 {@code sink.onTextDelta}、语音 PCM 分片经 {@code sink.onAudio} 推送给前端，
     * 结束后经 {@code sink.onDone} 返回已保存的双方消息。出错经 {@code sink.onError} 通知前端降级。
     */
    void streamInterviewReply(Long sessionId, MockInterviewRespondRequest req, StreamEventSink sink);

    /**
     * 主动收尾面试：生成总结评价并宣布面试结束，将会话置为 COMPLETED。
     */
    MockInterviewMessage concludeInterview(Long sessionId);

    /**
     * 获取某会话全部消息，按时间排序。
     */
    List<MockInterviewMessage> getMessages(Long sessionId);

    /**
     * 中途切换面试官人设：写入一条换人系统消息，后续回复使用新的人设风格。
     */
    void switchPersona(Long sessionId, Long newPersonaId);
}
