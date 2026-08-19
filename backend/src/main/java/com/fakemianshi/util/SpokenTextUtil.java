package com.fakemianshi.util;

import java.util.regex.Pattern;

/**
 * 口语化文本净化：移除代码块/行内代码等不适合语音播报的内容。
 *
 * <p>面试官回复会被 TTS 播报，代码无法朗读且严重影响体验，因此在 prompt 层禁止之外，
 * 再做一次文本层兜底净化（去掉 Markdown 代码围栏与行内代码符号）。
 */
public final class SpokenTextUtil {

    private SpokenTextUtil() {
    }

    private static final Pattern FENCED_BLOCK = Pattern.compile("```[\\s\\S]*?```");
    private static final Pattern INLINE_CODE = Pattern.compile("`([^`\\n]*)`");

    /** 语气/动作/神态说明（舞台指示），如“（语气冷淡）”“（稍缓）”“（停顿）”，中英文括号均匹配 */
    private static final Pattern STAGE_DIRECTION = Pattern.compile(
            "[\\(（][^（）()]{0,30}(语气|语速|停顿|声调|声音|情绪|表情|动作|神态|稍缓|放缓|严厉|冷淡|不耐烦|严肃|温和|加重|放慢|轻笑|叹气|微笑|皱眉|点头|摇头|眼神|摆手|示意)[^（）()]{0,30}[\\)）]");

    /** 移除 Markdown 代码围栏块，行内代码保留其文本内容（去掉反引号）。 */
    public static String stripCode(String text) {
        if (text == null || text.isBlank()) {
            return text;
        }
        String t = FENCED_BLOCK.matcher(text).replaceAll("");
        t = INLINE_CODE.matcher(t).replaceAll("$1");
        return t;
    }

    /** 移除括号内的语气/动作/神态说明（舞台指示），仅保留对话正文。 */
    public static String stripStageDirections(String text) {
        if (text == null || text.isBlank()) {
            return text;
        }
        return STAGE_DIRECTION.matcher(text).replaceAll("");
    }

    /** 净化用于语音播报与展示的文本：去代码 + 去舞台指示 + 合并多余空白。 */
    public static String sanitizeForSpeech(String text) {
        if (text == null) {
            return "";
        }
        String t = stripCode(text);
        t = stripStageDirections(t);
        t = t.replaceAll("[ \\t]+", " ");
        t = t.replaceAll("\\n{3,}", "\n\n");
        return t.trim();
    }
}
