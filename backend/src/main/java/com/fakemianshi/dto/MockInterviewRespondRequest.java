package com.fakemianshi.dto;

import lombok.Data;

/**
 * 模拟面试回复请求。
 */
@Data
public class MockInterviewRespondRequest {

    /** 候选人回答文本（STT 后的文字，语音在前端转好文字传过来） */
    private String userText;

    /** 音频文件路径，可空 */
    private String audioPath;
}
