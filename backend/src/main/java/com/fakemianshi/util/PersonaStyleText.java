package com.fakemianshi.util;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

/**
 * 面试官风格 {@code styleConfig}（JSON）→ 提示词文字。
 *
 * <p>与 TTS 侧的 {@code LocalQwenVoiceService.resolvePersonaVoice} 吃的是同一份 JSON，
 * 但**分工不同**：
 * <ul>
 *   <li>本类决定「问什么」——追问策略、语气、追问强度，进 LLM 系统提示词；</li>
 *   <li>TTS 侧决定「怎么念」——音色（{@code qwenVoice}）与风格指令（{@code qwenInstruct}）。</li>
 * </ul>
 *
 * <p>注意 {@code aggressiveness} 只有在这里才真正生效。0.6B 的 Qwen3-TTS 会丢弃
 * {@code instruct}，所以指望它去影响声音是无效的（详见 CONTEXT.md 的面试官风格表）。
 *
 * <p>抽成独立类是为了可测：这些映射改了以后面试官的脾气会变，必须能单独回归。
 */
public final class PersonaStyleText {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private PersonaStyleText() {
    }

    /** 把 styleConfig 翻译成一行提示词文字；无配置或解析失败时给出兜底描述。 */
    public static String toPromptText(String styleConfig) {
        if (styleConfig == null || styleConfig.isBlank()) {
            return "";
        }
        JsonNode node;
        try {
            node = MAPPER.readTree(styleConfig);
        } catch (JacksonException e) {
            // styleConfig 非 JSON 时降级为通用策略
            return "追问策略：根据回答动态深挖";
        }
        if (node == null || !node.isObject()) {
            return "追问策略：根据回答动态深挖";
        }
        List<String> parts = new ArrayList<>(3);
        parts.add("追问策略：" + followupDesc(node.path("followupStrategy").asText("").trim()));
        String tone = toneDesc(node.path("tone").asText("").trim());
        if (!tone.isEmpty()) {
            parts.add("语气：" + tone);
        }
        JsonNode aggressiveness = node.path("aggressiveness");
        if (aggressiveness.isNumber()) {
            parts.add("追问强度：" + aggressivenessDesc(aggressiveness.asDouble()));
        }
        return String.join("；", parts);
    }

    /** 追问策略：决定追问的**方向** */
    public static String followupDesc(String followup) {
        return switch (followup) {
            case "deep_dive" -> "不断追问底层原理和实现细节，刨根问底到源码级别";
            case "challenge" -> "质疑回答、施加压力，重点考察抗压能力";
            case "guide" -> "友好引导，答不上来时给提示并换角度提问";
            case "project_drill" -> "围绕简历项目深挖架构决策、踩坑经验与技术选型理由";
            case "checklist" -> "按知识点清单逐个考察，不追问过深";
            default -> followup.isBlank() ? "根据回答动态深挖" : followup;
        };
    }

    /** 语气：决定措辞的**温度** */
    public static String toneDesc(String tone) {
        return switch (tone == null ? "" : tone) {
            case "serious" -> "冷静克制、措辞严谨，不寒暄也不夸奖";
            case "stern" -> "直接、有压迫感，句子短促，不用缓冲语";
            case "warm" -> "温和鼓励，像带新人的资深工程师，先肯定再追问";
            case "professional" -> "稳重、注重细节，只就事论事";
            case "neutral" -> "平稳自然";
            default -> "";
        };
    }

    /**
     * 追问强度（0~1）：决定追问的**烈度与层数**。
     *
     * <p>以前这个字段只被塞进 TTS 的 instruct，而 0.6B 模型会丢弃 instruct，
     * 等于完全没用。现在它真的会改变面试官的行为。
     */
    public static String aggressivenessDesc(double value) {
        if (value >= 0.8) {
            return "高压。可以质疑候选人的结论并要求给出数据与归因，不接受“大概”“应该”“差不多”这类含糊回答；"
                    + "同一问题连续追问三层以上直到问出根因，候选人跳过关键步骤时当场指出并拉回来";
        }
        if (value >= 0.6) {
            return "偏严。对含糊或跳步的回答必须追问清楚，同一问题连续追问 2 到 3 层";
        }
        if (value >= 0.3) {
            return "中性。候选人答不上来时给一次提示，仍答不出就换个角度，不连续施压";
        }
        return "温和。候选人卡住时主动给提示或把问题拆小，允许换题，不打断";
    }
}
