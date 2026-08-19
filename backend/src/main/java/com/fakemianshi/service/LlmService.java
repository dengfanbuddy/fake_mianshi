package com.fakemianshi.service;

import com.fakemianshi.dto.LlmResponse;

import java.util.List;

/**
 * 大语言模型（LLM）服务：负责与已配置的 AI 模型交互。
 */
public interface LlmService {

    /**
     * 以 system + user 两条消息对话（systemPrompt 为空时只发 user 消息）。
     */
    LlmResponse chat(String systemPrompt, String userPrompt);

    /**
     * 以 system + user 两条消息对话，可指定输出最大 token 数（长文本分析任务用）。
     */
    LlmResponse chat(String systemPrompt, String userPrompt, int maxTokens);

    /**
     * 以完整的消息列表对话，可自由控制 role（system / user / assistant）。
     */
    LlmResponse chat(List<Message> messages);

    /**
     * 以完整的消息列表对话，可指定输出最大 token 数（长文本分析任务用）。
     */
    LlmResponse chat(List<Message> messages, int maxTokens);

    /**
     * 流式对话：启用 stream 模式，输出内容通过 onDelta 逐段回调（用于 SSE 推送），
     * 思考型模型的思考过程经 onReasoning 回调（可为 null，不关心则忽略），
     * 返回完整拼接后的内容文本。
     *
     * @param onDelta     输出内容增量回调（可为 null）
     * @param onReasoning 思考过程增量回调（可为 null）
     */
    String chatStream(String systemPrompt, String userPrompt, int maxTokens,
                      java.util.function.Consumer<String> onDelta,
                      java.util.function.Consumer<String> onReasoning);

    /**
     * 流式对话（完整消息列表版）：与 {@link #chatStream(String, String, int, java.util.function.Consumer, java.util.function.Consumer)}
     * 相同，但可自由控制 role（system / user / assistant），用于携带完整对话历史的模拟面试。
     */
    String chatStream(List<Message> messages, int maxTokens,
                      java.util.function.Consumer<String> onDelta,
                      java.util.function.Consumer<String> onReasoning);

    /**
     * 对话消息。
     */
    record Message(String role, String content) {}
}
