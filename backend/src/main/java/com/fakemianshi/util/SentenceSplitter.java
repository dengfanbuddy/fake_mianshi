package com.fakemianshi.util;

import java.util.ArrayList;
import java.util.List;

/**
 * 文本分句工具：将一段面试官回复按句子边界切分，供逐句 TTS 使用。
 *
 * <p>腾讯云基础语音合成（TextToVoice）单次文本有长度上限（约 150 字），
 * 因此长回复必须按句切分后逐句合成再拼接。切分时保留句末标点，保证语调自然。
 */
public final class SentenceSplitter {

    private SentenceSplitter() {
    }

    /** 默认单句最大字符数，超过则硬切（对齐腾讯 TTS 单次文本上限） */
    public static final int DEFAULT_MAX_CHARS = 150;

    /** 句子结束标点（切分后保留标点） */
    private static final String END_MARKS = "。！？；!?;\n";

    public static List<String> split(String text) {
        return split(text, DEFAULT_MAX_CHARS);
    }

    /**
     * 按句末标点切分；单句超过 {@code maxChars} 时按长度硬切，避免超出 TTS 单次上限。
     */
    public static List<String> split(String text, int maxChars) {
        List<String> result = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return result;
        }
        int safeMax = maxChars > 0 ? maxChars : DEFAULT_MAX_CHARS;
        StringBuilder buf = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            buf.append(c);
            boolean isEnd = END_MARKS.indexOf(c) >= 0;
            boolean tooLong = buf.length() >= safeMax;
            if (isEnd || tooLong) {
                flush(result, buf);
            }
        }
        flush(result, buf);
        return result;
    }

    private static void flush(List<String> result, StringBuilder buf) {
        if (buf.length() > 0) {
            String seg = buf.toString().trim();
            if (!seg.isEmpty()) {
                result.add(seg);
            }
            buf.setLength(0);
        }
    }
}
