package com.fakemianshi.util;

/**
 * LLM 输出 JSON 提取工具。
 *
 * <p>LLM 有时会输出 Markdown 代码块围栏（```json ... ```）或在 JSON 前后夹带解释文字。
 * 本工具负责：1) 去除围栏；2) 截取首个起始符到最后一个结束符之间的 JSON 主体。
 * 供需要解析 LLM 返回 JSON 的服务（如分析服务、出题引擎）复用。
 */
public final class JsonExtractor {

    private JsonExtractor() {
    }

    /**
     * 提取 JSON 数组主体：取首个 [ 到最后一个 ]；未找到数组时返回清理后的全文，由调用方继续解析。
     */
    public static String extractJsonArray(String llmOutput) {
        return extract(llmOutput, '[', ']');
    }

    /**
     * 提取 JSON 对象主体：取首个 { 到最后一个 }；未找到对象时返回清理后的全文，由调用方继续解析。
     */
    public static String extractJsonObject(String llmOutput) {
        return extract(llmOutput, '{', '}');
    }

    private static String extract(String llmOutput, char open, char close) {
        if (llmOutput == null || llmOutput.isBlank()) {
            return "";
        }
        String text = llmOutput.trim();
        // 去除 ```json / ``` Markdown 围栏
        if (text.startsWith("```")) {
            int firstNewline = text.indexOf('\n');
            if (firstNewline >= 0) {
                text = text.substring(firstNewline + 1);
            }
            if (text.endsWith("```")) {
                text = text.substring(0, text.length() - 3);
            }
            text = text.trim();
        }
        int start = text.indexOf(open);
        int end = text.lastIndexOf(close);
        if (start >= 0 && end > start) {
            return text.substring(start, end + 1);
        }
        return text;
    }
}
