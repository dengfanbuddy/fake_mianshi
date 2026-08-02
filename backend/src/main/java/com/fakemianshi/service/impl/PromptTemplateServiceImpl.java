package com.fakemianshi.service.impl;

import com.fakemianshi.config.BusinessException;
import com.fakemianshi.dto.PromptTemplateDTO;
import com.fakemianshi.entity.PromptTemplate;
import com.fakemianshi.repository.PromptTemplateRepository;
import com.fakemianshi.service.LlmService;
import com.fakemianshi.service.PromptTemplateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 面试提示词模板服务实现。
 *
 * <p>default 模板内置在代码中（通用兜底），首次启动写入数据库便于页面查看/编辑；
 * 业务侧按 职业 → default → 内置 的优先级取模板，{position} 占位符替换为目标岗位。
 * 新职业出现时由 AI 基于 default 模板生成该职业专属模板并保存。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PromptTemplateServiceImpl implements PromptTemplateService {

    private final PromptTemplateRepository templateRepository;
    private final LlmService llmService;

    /** 笔试出题默认模板（通用） */
    private static final String DEFAULT_WRITTEN_QUESTION = """
            你是资深面试出题官，负责为「{position}」岗位生成笔试题。
            请根据职位需求、候选人简历、历史弱点标签生成题目。
            要求：
            1. 题目要贴合岗位实际工作场景，覆盖该岗位的核心知识、技能与典型问题；
            2. 单选题/多选题要有明确的唯一（或确定组合）答案，选项表达清晰；
            3. 填空题答案简短明确；简答题给出参考答案要点；
            4. 每道题标注 knowledgePoints（该题考察的知识点，必须是岗位相关的具体知识点）；
            5. 题量按给定数量生成，难度从基础到进阶合理分布；
            6. 题型分布：单选 50%、多选 15%、填空 10%、简答 25%（可微调）。
            请严格返回如下 JSON 数组，不要输出任何额外文字或 Markdown 代码块，数组每项格式：
            {"type":"SINGLE_CHOICE","content":"...","options":["A","B","C","D"],"answer":"A","explanation":"...","knowledgePoints":["知识点"]}
            说明：
            - type 取值：SINGLE_CHOICE / MULTIPLE_CHOICE / FILL_BLANK / SHORT_ANSWER；
            - 单选 answer 为选项字母，多选为字母组合（如 "ABD"），填空/简答为答案文本；
            - 所有题目内容必须是该岗位相关的真实业务场景问题。
            """;

    /** 模拟面试大纲默认模板（通用） */
    private static final String DEFAULT_MOCK_OUTLINE = """
            你是资深面试官，负责为「{position}」岗位的候选人制定模拟面试大纲。
            请根据职位需求、候选人简历、历史弱点标签，规划面试考察重点。
            要求：
            1. 考察维度结合岗位特点：专业技能（该岗位核心技能）、项目经验（简历深挖）、
               通用能力（沟通/抗压/职业规划）等，按岗位重要性分配；
            2. 每个考察点给出具体主题（必须是岗位相关的具体技能点或能力项）；
            3. 优先覆盖候选人简历中体现的技术栈/经历与历史弱点标签。
            请严格返回如下 JSON 结构，不要输出任何额外文字或 Markdown 代码块：
            {"sections":[{"type":"TECHNICAL","topics":["岗位核心技能点"]},{"type":"PROJECT","topics":["简历项目深挖"]},{"type":"BEHAVIORAL","topics":["离职原因","职业规划"]}],"estimatedQuestions":15,"focusPoints":["面试重点"]}
            说明：
            - type 取值：TECHNICAL / PROJECT / BEHAVIORAL / OTHER；
            - topics 必须是该岗位具体相关的主题。
            """;

    /** 笔试分析默认模板（通用） */
    private static final String DEFAULT_WRITTEN_ANALYSIS = """
            你是资深面试官。请分析以下「{position}」岗位的笔试结果，为候选人生成一份查漏补缺的分析报告。
            要求：
            1. 对自动判分的客观题（单选/多选/填空）指出答错原因与正确思路；
            2. 对未自动判分的简答题给出准确度评分和完整参考答案要点；
            3. strengths、weaknesses 各列出 2-5 条具体表现；
            4. knowledgeGaps 必须是该岗位的具体知识点（如岗位核心技术点），每项附 explanation：该知识点的核心答案要点（100-200 字）；
            5. improvementPlan.topics 每项给出 {topic, action, example}，suggestions 给出可执行建议；
            6. 根据面试表现给出 overallLevel（初级/中级/高级/资深/专家 工程师或对应职级）与 expectedSalaryRange（按国内一线城市行情，如 "20k-30k"）；
            7. personalitySummary 总结候选人性格与工作风格（100 字内）；characterTraits 2-4 个；characterDefects 1-3 个及改进方法；
            8. questionAnalyses 每项给出 category（岗位技术分类）、difficulty（初级/中级/高级）、focusPoint、answerApproach、example；
            9. 每个评分维度给出打分依据（accuracyReason/depthReason/clarityReason/fluencyReason，各 30-80 字）。
            请严格返回如下 JSON 结构，不要输出任何额外文字或 Markdown 代码块：
            {"overallScore":0-100,"overallLevel":"...","expectedSalaryRange":"...","strengths":["..."],"weaknesses":["..."],"knowledgeGaps":[{"point":"知识点","explanation":"答案要点"}],"personalitySummary":"...","characterTraits":["..."],"characterDefects":[{"defect":"缺陷","improvement":"改进方法"}],"improvementPlan":{"topics":[{"topic":"学习方向","action":"具体做法","example":"实例"}],"suggestions":["建议"]},"questionAnalyses":[{"questionContent":"...","answerContent":"...","category":"技术分类","difficulty":"难度","focusPoint":"考察方向","accuracy":0-10,"accuracyReason":"依据","depth":0-10,"depthReason":"依据","clarity":0-10,"clarityReason":"依据","fluency":0-10,"fluencyReason":"依据","tone":"","answerApproach":"回答思路","example":"优秀示例","improvementSuggestion":"..."}]}
            说明：
            - questionAnalyses 每项对应一道笔试题，questionContent 请原样引用题目内容；
            - 客观题 fluency 填 0；accuracy/depth/clarity/fluency 均按 0-10 打分。
            """;

    /** 模拟面试分析默认模板（通用） */
    private static final String DEFAULT_MOCK_ANALYSIS = """
            你是资深面试官。请分析以下「{position}」岗位的模拟面试对话，为候选人生成一份查漏补缺的分析报告。
            要求：
            1. 结合对话逐题评估候选人的回答：accuracy（准确度）、depth（深度）、clarity（清晰度）、fluency（流畅度）；
            2. communicationEvaluation 评价表达、逻辑、语气、流畅度，不超过 500 字；
            3. strengths、weaknesses 各列出 2-5 条具体表现；
            4. knowledgeGaps 必须是该岗位的具体知识点，每项附 explanation（核心答案要点 100-200 字）；
            5. improvementPlan.topics 每项给出 {topic, action, example}；
            6. 根据面试表现给出 overallLevel 与 expectedSalaryRange（国内一线城市行情）；
            7. personalitySummary（100 字内）、characterTraits 2-4 个、characterDefects 1-3 个及改进方法；
            8. questionAnalyses 每项给出 category、difficulty、focusPoint、answerApproach、example；
            9. 每个评分维度给出打分依据（30-80 字）。
            请严格返回如下 JSON 结构，不要输出任何额外文字或 Markdown 代码块：
            {"overallScore":0-100,"overallLevel":"...","expectedSalaryRange":"...","strengths":["..."],"weaknesses":["..."],"knowledgeGaps":[{"point":"知识点","explanation":"答案要点"}],"personalitySummary":"...","characterTraits":["..."],"characterDefects":[{"defect":"缺陷","improvement":"改进方法"}],"communicationEvaluation":"...","improvementPlan":{"topics":[{"topic":"学习方向","action":"具体做法","example":"实例"}],"suggestions":["建议"]},"questionAnalyses":[{"questionContent":"...","answerContent":"...","category":"技术分类","difficulty":"难度","focusPoint":"考察方向","accuracy":0-10,"accuracyReason":"依据","depth":0-10,"depthReason":"依据","clarity":0-10,"clarityReason":"依据","fluency":0-10,"fluencyReason":"依据","tone":"","answerApproach":"回答思路","example":"优秀示例","improvementSuggestion":"..."}]}
            说明：
            - questionAnalyses 每项对应一轮问答，questionContent 请引用面试官的原问题；
            - accuracy/depth/clarity/fluency 均按 0-10 打分。
            """;

    /** 历史综合分析默认模板（通用） */
    private static final String DEFAULT_HISTORY_ANALYSIS = """
            你是资深面试官，负责为「{position}」岗位候选人的长期学习做规划。
            请基于以下多次面试（笔试/模拟面试）的分析结果，输出历史综合分析报告。
            要求：
            1. trends 至少包含「总体评分」「专业技能」「沟通表达」三个维度（缺失可省略），trend 取 上升/平稳/下降；
            2. recurringWeaknesses 列出多次面试中反复出现的薄弱知识点（该岗位具体知识点）；
            3. recommendedFocus 给出 3-5 个下次重点练习方向；
            4. overallProgress 用一段话整体评价候选人的成长情况。
            请严格返回如下 JSON 结构，不要输出任何额外文字或 Markdown 代码块：
            {"trends":[{"dimension":"专业技能","trend":"上升/平稳/下降","detail":"说明"}],"recurringWeaknesses":["反复出现的弱点知识点"],"recommendedFocus":["下次重点练习"],"overallProgress":"整体评价"}
            """;

    private static final Map<String, String> DEFAULT_TEMPLATES = new LinkedHashMap<>();

    static {
        DEFAULT_TEMPLATES.put(Scene.WRITTEN_QUESTION.name(), DEFAULT_WRITTEN_QUESTION);
        DEFAULT_TEMPLATES.put(Scene.MOCK_OUTLINE.name(), DEFAULT_MOCK_OUTLINE);
        DEFAULT_TEMPLATES.put(Scene.WRITTEN_ANALYSIS.name(), DEFAULT_WRITTEN_ANALYSIS);
        DEFAULT_TEMPLATES.put(Scene.MOCK_ANALYSIS.name(), DEFAULT_MOCK_ANALYSIS);
        DEFAULT_TEMPLATES.put(Scene.HISTORY_ANALYSIS.name(), DEFAULT_HISTORY_ANALYSIS);
    }

    /** 启动时确保 default 模板入库（页面可见/可编辑） */
    @jakarta.annotation.PostConstruct
    public void initDefaultTemplates() {
        for (Map.Entry<String, String> entry : DEFAULT_TEMPLATES.entrySet()) {
            if (templateRepository.findByOccupationAndScene(DEFAULT_OCCUPATION, entry.getKey()).isEmpty()) {
                PromptTemplate t = new PromptTemplate();
                t.setOccupation(DEFAULT_OCCUPATION);
                t.setScene(entry.getKey());
                t.setContent(entry.getValue());
                t.setIsActive(true);
                t.setUpdatedAt(LocalDateTime.now());
                templateRepository.insert(t);
            }
        }
        log.info("提示词默认模板初始化完成（{} 个场景）", DEFAULT_TEMPLATES.size());
    }

    @Override
    @Transactional(readOnly = true)
    public String getTemplate(String occupation, String scene, String position) {
        String content = null;
        // 1. 职业模板
        if (occupation != null && !occupation.isBlank() && !DEFAULT_OCCUPATION.equals(occupation)) {
            content = templateRepository.findByOccupationAndScene(occupation, scene)
                    .map(PromptTemplate::getContent).orElse(null);
        }
        // 2. default 模板（库）
        if (content == null) {
            content = templateRepository.findByOccupationAndScene(DEFAULT_OCCUPATION, scene)
                    .map(PromptTemplate::getContent).orElse(null);
        }
        // 3. 内置兜底
        if (content == null) {
            content = DEFAULT_TEMPLATES.get(scene);
        }
        if (content == null) {
            throw new BusinessException("未找到提示词模板: scene=" + scene);
        }
        String pos = (position == null || position.isBlank()) ? "目标岗位" : position.trim();
        return content.replace("{position}", pos);
    }

    @Override
    public List<String> ensureOccupationTemplates(String occupation) {
        List<String> generated = new ArrayList<>();
        if (occupation == null || occupation.isBlank() || DEFAULT_OCCUPATION.equals(occupation.trim())) {
            return generated;
        }
        String occ = occupation.trim();
        for (Scene scene : Scene.values()) {
            if (templateRepository.findByOccupationAndScene(occ, scene.name()).isEmpty()) {
                try {
                    regenerateByAI(occ, scene.name());
                    generated.add(scene.name());
                } catch (Exception e) {
                    log.warn("职业提示词 AI 生成失败: occupation={}, scene={}, cause={}",
                            occ, scene.name(), e.getMessage());
                }
            }
        }
        return generated;
    }

    @Override
    @Transactional
    public void saveTemplate(String occupation, String scene, String content) {
        String occ = (occupation == null || occupation.isBlank()) ? DEFAULT_OCCUPATION : occupation.trim();
        Optional<PromptTemplate> existing = templateRepository.findByOccupationAndScene(occ, scene);
        if (existing.isPresent()) {
            PromptTemplate t = existing.get();
            t.setContent(content);
            t.setIsActive(true);
            t.setUpdatedAt(LocalDateTime.now());
            templateRepository.updateById(t);
        } else {
            PromptTemplate t = new PromptTemplate();
            t.setOccupation(occ);
            t.setScene(scene);
            t.setContent(content);
            t.setIsActive(true);
            t.setUpdatedAt(LocalDateTime.now());
            templateRepository.insert(t);
        }
    }

    @Override
    public String regenerateByAI(String occupation, String scene) {
        String occ = (occupation == null || occupation.isBlank()) ? DEFAULT_OCCUPATION : occupation.trim();
        // 参考模板：同场景现有模板（职业版优先，否则 default）
        String reference = templateRepository.findByOccupationAndScene(occ, scene)
                .map(PromptTemplate::getContent)
                .orElseGet(() -> templateRepository.findByOccupationAndScene(DEFAULT_OCCUPATION, scene)
                        .map(PromptTemplate::getContent)
                        .orElseGet(() -> DEFAULT_TEMPLATES.get(scene)));
        Scene sceneEnum = resolveScene(scene);

        String sysPrompt = """
                你是 AI 提示词工程师，擅长为不同职业定制面试系统的 AI 提示词。
                请参考下面的通用提示词模板，为「%s」职业改写一份专属提示词。
                要求：
                1. 保持整体结构和输出格式（JSON 结构、字段、评分规则）完全不变；
                2. 把"岗位相关"的具体内容（题目场景、技术/技能点示例、知识盲区方向、考察维度）替换为该职业特有的；
                3. 语气保持专业面试官风格；不要输出任何额外解释，直接输出改写后的提示词全文。
                """.formatted(occ);

        String userPrompt = "【通用提示词模板】\n" + reference;
        com.fakemianshi.dto.LlmResponse resp = llmService.chat(sysPrompt, userPrompt,
                LlmServiceImpl.LONG_TASK_MAX_TOKENS);
        String generated = resp.getContent() == null ? "" : resp.getContent().trim();
        if (generated.isBlank()) {
            throw new BusinessException("AI 生成提示词为空，请重试");
        }
        saveTemplate(occ, sceneEnum.name(), generated);
        return generated;
    }

    @Override
    @Transactional(readOnly = true)
    public List<PromptTemplateDTO> listTemplates(String occupation) {
        List<PromptTemplate> list = (occupation == null || occupation.isBlank())
                ? templateRepository.findAllActive()
                : templateRepository.findByOccupation(occupation.trim());
        List<PromptTemplateDTO> result = new ArrayList<>();
        for (PromptTemplate t : list) {
            PromptTemplateDTO dto = new PromptTemplateDTO();
            dto.setId(t.getId());
            dto.setOccupation(t.getOccupation());
            dto.setScene(t.getScene());
            dto.setSceneLabel(resolveScene(t.getScene()).getLabel());
            dto.setContent(t.getContent());
            dto.setUpdatedAt(t.getUpdatedAt());
            result.add(dto);
        }
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> listOccupations() {
        List<String> list = templateRepository.findOccupations();
        if (!list.contains(DEFAULT_OCCUPATION)) {
            list.add(0, DEFAULT_OCCUPATION);
        }
        return list;
    }

    private Scene resolveScene(String scene) {
        for (Scene s : Scene.values()) {
            if (s.name().equalsIgnoreCase(scene)) {
                return s;
            }
        }
        throw new BusinessException("未知提示词场景: " + scene);
    }
}
