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
     * 对话消息。
     */
    record Message(String role, String content) {}
}
