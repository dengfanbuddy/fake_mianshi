package com.fakemianshi.dto;

import lombok.Data;

/**
 * 语音合成（TTS）请求体。
 */
@Data
public class TtsRequest {

    /** 待合成的文本 */
    private String text;

    /** 面试官人设风格配置（JSON 字符串），由服务端映射为腾讯云音色编号 */
    private String voiceType;
}
