package com.fakemianshi.service;

import com.fakemianshi.dto.PromptTemplateDTO;

import java.util.List;

/**
 * 面试提示词模板服务：按职业（occupation）× 场景（scene）分类管理，
 * 支持查看/编辑/AI 生成，业务调用时按职业回退 default 模板。
 */
public interface PromptTemplateService {

    /** 提示词场景枚举 */
    enum Scene {
        /** 笔试出题 */
        WRITTEN_QUESTION("笔试出题"),
        /** 模拟面试大纲 */
        MOCK_OUTLINE("模拟面试大纲"),
        /** 笔试分析报告 */
        WRITTEN_ANALYSIS("笔试分析报告"),
        /** 模拟面试分析报告 */
        MOCK_ANALYSIS("模拟面试分析报告"),
        /** 历史综合分析 */
        HISTORY_ANALYSIS("历史综合分析");

        private final String label;

        Scene(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }
    }

    /** 通用兜底职业 */
    String DEFAULT_OCCUPATION = "default";

    /**
     * 获取某职业某场景的提示词模板文本：
     * 职业模板 → default 模板 → 内置兜底文本，替换 {position} 占位符后返回。
     */
    String getTemplate(String occupation, String scene, String position);

    /**
     * 确保某职业的提示词模板齐全：缺哪个场景用 AI 基于 default 模板生成并保存。
     *
     * @return 本次新生成的场景列表（空 = 已齐全）
     */
    List<String> ensureOccupationTemplates(String occupation);

    /**
     * 保存/更新某职业某场景的模板（编辑）。
     */
    void saveTemplate(String occupation, String scene, String content);

    /**
     * AI 针对职业重新生成某场景模板（基于 default 模板改写），并保存。
     */
    String regenerateByAI(String occupation, String scene);

    /**
     * 查询某职业的全部模板（管理页展示）；occupation 为 null 时返回全部职业。
     */
    List<PromptTemplateDTO> listTemplates(String occupation);

    /**
     * 已配置的职业列表（含 default）。
     */
    List<String> listOccupations();
}
