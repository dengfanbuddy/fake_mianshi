package com.fakemianshi.dto;

import com.fakemianshi.entity.MockInterviewMessage;
import lombok.Data;

/**
 * 模拟面试回复响应。
 */
@Data
public class MockInterviewRespondResponse {

    /** 候选人本轮消息（已保存） */
    private MockInterviewMessage userMessage;

    /** 面试官 AI 回复消息（已保存） */
    private MockInterviewMessage aiMessage;
}
