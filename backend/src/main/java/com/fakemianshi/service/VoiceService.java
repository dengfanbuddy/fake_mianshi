package com.fakemianshi.service;

/**
 * 语音服务：负责与腾讯云 ASR（语音识别）和 TTS（语音合成）交互。
 */
public interface VoiceService {

    /**
     * 语音识别（STT）：调用腾讯云一句话识别，将音频数据转换为文本。
     *
     * @param audioData 原始音频字节（wav/mp3 等格式）
     * @return 识别出的文本
     */
    String recognizeSpeech(byte[] audioData);

    /**
     * 语音合成（TTS）：调用腾讯云基础语音合成，将文本转换为 wav 音频。
     *
     * @param text      待合成的文本
     * @param voiceType 面试官人设风格配置（JSON 字符串，由调用方传入，方法内解析映射音色）
     * @return 合成的 wav 音频字节
     */
    byte[] synthesizeSpeech(String text, String voiceType);
}
