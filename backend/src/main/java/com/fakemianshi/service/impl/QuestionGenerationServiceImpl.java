package com.fakemianshi.service.impl;

import com.fakemianshi.config.BusinessException;
import com.fakemianshi.dto.LlmResponse;
import com.fakemianshi.entity.InterviewerPersona;
import com.fakemianshi.entity.PositionRequirement;
import com.fakemianshi.entity.Resume;
import com.fakemianshi.entity.WeaknessTag;
import com.fakemianshi.entity.WrittenTestQuestion;
import com.fakemianshi.repository.InterviewerPersonaRepository;
import com.fakemianshi.repository.WeaknessTagRepository;
import com.fakemianshi.repository.WrittenTestQuestionRepository;
import com.fakemianshi.service.LlmService;
import com.fakemianshi.service.impl.LlmServiceImpl;
import com.fakemianshi.service.PositionRequirementService;
import com.fakemianshi.service.QuestionGenerationService;
import com.fakemianshi.service.ResumeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 出题引擎实现：将职位需求、简历、弱点标签组装为 LLM 上下文，
 * 调用 LLM 生成笔试题 / 模拟面试大纲 / 面试官开场白。
 */
@Service
@RequiredArgsConstructor
public class QuestionGenerationServiceImpl implements QuestionGenerationService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /** 简历原文截断长度，避免上下文过大 */
    private static final int RESUME_PARSED_TEXT_LIMIT = 3000;

    private final LlmService llmService;
    private final ResumeService resumeService;
    private final PositionRequirementService positionRequirementService;
    private final WeaknessTagRepository weaknessTagRepository;
    private final WrittenTestQuestionRepository writtenTestQuestionRepository;
    private final InterviewerPersonaRepository interviewerPersonaRepository;
    private final com.fakemianshi.service.PromptTemplateService promptTemplateService;
    private final com.fakemianshi.repository.InterviewProjectRepository interviewProjectRepository;

    @Override
    @Transactional
    public List<WrittenTestQuestion> generateWrittenTestQuestions(Long sessionId, Long projectId, int questionCount) {
        String systemPrompt = buildWrittenTestPrompt(projectId, questionCount);
        String userPrompt = buildContext(projectId);
        LlmResponse response = llmService.chat(systemPrompt, userPrompt, LlmServiceImpl.LONG_TASK_MAX_TOKENS);
        List<JsonNode> items = parseQuestionArray(extractJsonSection(response.getContent()));

        return saveQuestions(items, sessionId);
    }

    @Override
    @Transactional
    public List<WrittenTestQuestion> generateWrittenTestQuestionsStream(
            Long sessionId, Long projectId, int questionCount,
            java.util.function.Consumer<String> onDelta,
            java.util.function.Consumer<String> onReasoning) {
        String systemPrompt = buildWrittenTestPrompt(projectId, questionCount);
        String userPrompt = buildContext(projectId);
        String fullOutput = llmService.chatStream(systemPrompt, userPrompt,
                LlmServiceImpl.LONG_TASK_MAX_TOKENS, onDelta, onReasoning);
        List<JsonNode> items = parseQuestionArray(extractJsonSection(fullOutput));
        return saveQuestions(items, sessionId);
    }

    /**
     * 构建笔试出题 system prompt：要求严格输出 JSON 数组（流式推送原文供前端实时展示）。
     */
    private String buildWrittenTestPrompt(Long projectId, int questionCount) {
        String template = promptTemplateService.getTemplate(
                resolveOccupation(projectId),
                com.fakemianshi.service.PromptTemplateService.Scene.WRITTEN_QUESTION.name(),
                resolvePosition(projectId));
        // 注入实际题数：优先替换 {questionCount} 占位，否则替换"给定数量"措辞（兼容旧模板）
        if (template.contains("{questionCount}")) {
            return template.replace("{questionCount}", String.valueOf(questionCount));
        }
        return template.replace("给定数量", String.valueOf(questionCount) + " 道");
    }

    /** 项目目标岗位（职业）；无则 null（走 default 模板） */
    private String resolveOccupation(Long projectId) {
        if (projectId == null) {
            return null;
        }
        com.fakemianshi.entity.InterviewProject project = interviewProjectRepository.selectById(projectId);
        return project == null ? null : project.getTargetPosition();
    }

    /** 项目目标岗位文本（用于 {position} 占位替换） */
    private String resolvePosition(Long projectId) {
        String occupation = resolveOccupation(projectId);
        return occupation == null ? "" : occupation;
    }

    /* 预留：原静态出题提示词已迁移至 prompt_template 默认模板 */
    private String buildWrittenTestPromptLegacy(int questionCount) {
        return """
                你是资深 Java 面试出题官。请根据职位需求、简历、弱点标签生成 %d 道笔试题。
                题型分配：单选30%%，多选20%%，填空20%%，简答30%%。
                要求：
                1. 覆盖简历技术栈中的核心知识点；
                2. 弱点标签中列出的知识点必须出题；
                3. 题目难度要适配候选人资历级别；
                4. 题目要贴近真实面试。

                请严格返回如下 JSON 数组，不要输出任何额外文字或 Markdown 代码块，数组每项格式：
                {"type":"SINGLE_CHOICE","content":"...","options":["A","B","C","D"],"answer":"A","explanation":"...","knowledgePoints":["JVM"]}
                说明：
                - 填空题/简答题的 options 为 null；
                - type 枚举：SINGLE_CHOICE / MULTIPLE_CHOICE / FILL_BLANK / SHORT_ANSWER；
                - JSON 数组项数必须与题目数量一致。
                """.formatted(questionCount);
    }

    /** 从双输出中提取 ==JSON_START== 之后的 JSON 部分 */
    private String extractJsonSection(String output) {
        if (output == null) {
            return "";
        }
        int idx = output.indexOf("==JSON_START==");
        if (idx < 0) {
            return output;
        }
        return output.substring(idx + "==JSON_START==".length());
    }

    private List<WrittenTestQuestion> saveQuestions(List<JsonNode> items, Long sessionId) {
        List<WrittenTestQuestion> questions = new ArrayList<>();
        int orderNum = 1;
        for (JsonNode item : items) {
            WrittenTestQuestion question = toQuestion(item, sessionId, orderNum++);
            writtenTestQuestionRepository.insert(question);
            questions.add(question);
        }
        return questions;
    }

    @Override
    @Transactional(readOnly = true)
    public String generateMockInterviewOutline(Long projectId) {
        String systemPrompt = promptTemplateService.getTemplate(
                resolveOccupation(projectId),
                com.fakemianshi.service.PromptTemplateService.Scene.MOCK_OUTLINE.name(),
                resolvePosition(projectId));
        return generateMockInterviewOutline(projectId, systemPrompt);
    }

    /** 带外部系统提示的面试大纲生成（保留旧实现逻辑） */
    public String generateMockInterviewOutline(Long projectId, String systemPrompt) {
        String userPrompt = buildContext(projectId);
        return llmService.chat(systemPrompt, userPrompt).getContent();
    }

    @Override
    @Transactional(readOnly = true)
    public String generateOpeningMessage(Long projectId, Long personaId) {
        InterviewerPersona persona = Optional.ofNullable(interviewerPersonaRepository.selectById(personaId))
                .orElseThrow(() -> new BusinessException("面试官人设不存在: id=" + personaId));

        PositionRequirement req = positionRequirementService.getByProjectId(projectId);
        String jobTitle = req == null ? "目标岗位" : valueOrDash(req.getJobTitle());
        String experience = req == null ? "未知" : valueOrDash(req.getExperience());

        String systemPrompt = """
                你是%s，面试风格是：%s。
                请用一句话进行开场自我介绍并开始面试，语气要符合你的风格。
                候选人应聘岗位：%s，经验：%s。
                要求：只说一句自然、贴合风格的开场白，不要输出 JSON 或任何额外解释。
                """.formatted(valueOrDash(persona.getName()), valueOrDash(persona.getDescription()), jobTitle, experience);

        return llmService.chat(systemPrompt, "请开始面试。").getContent();
    }

    /**
     * 组装给 LLM 的上下文：职位需求 + 简历 + 弱点标签。
     * 弱点标签会明确标注为「掌握较弱，出题优先覆盖」。
     */
    private String buildContext(Long projectId) {
        StringBuilder sb = new StringBuilder();

        sb.append("【职位需求】\n");
        PositionRequirement req = positionRequirementService.getByProjectId(projectId);
        if (req == null) {
            sb.append("（无）\n");
        } else {
            sb.append("- 岗位名称：").append(valueOrDash(req.getJobTitle())).append('\n');
            sb.append("- 职级要求：").append(valueOrDash(req.getSeniority())).append('\n');
            sb.append("- 公司类型：").append(valueOrDash(req.getCompanyType())).append('\n');
            sb.append("- 技术栈：").append(valueOrDash(req.getTechStack())).append('\n');
            sb.append("- 岗位职责：").append(valueOrDash(req.getJobDescription())).append('\n');
        }

        sb.append("\n【候选人简历】\n");
        Resume resume = resumeService.getLatestByProjectId(projectId);
        if (resume == null) {
            sb.append("（无）\n");
        } else if (resume.getAnalysisResult() != null && !resume.getAnalysisResult().isBlank()) {
            sb.append("简历分析结果：\n").append(resume.getAnalysisResult()).append('\n');
        } else {
            String parsed = resume.getParsedText();
            if (parsed != null && parsed.length() > RESUME_PARSED_TEXT_LIMIT) {
                parsed = parsed.substring(0, RESUME_PARSED_TEXT_LIMIT);
            }
            sb.append("简历原文：\n").append(valueOrDash(parsed)).append('\n');
        }

        sb.append("\n【弱点标签】\n");
        List<WeaknessTag> tags = weaknessTagRepository.findByProjectId(projectId);
        if (tags.isEmpty()) {
            sb.append("（无）\n");
        } else {
            sb.append("以下知识点用户掌握较弱，出题时应优先覆盖：\n");
            for (WeaknessTag tag : tags) {
                sb.append("- ").append(valueOrDash(tag.getKnowledgePoint()))
                        .append("（掌握程度：").append(valueOrDash(tag.getMasteryLevel())).append("）\n");
            }
        }
        return sb.toString();
    }

    /**
     * 解析 LLM 返回的题目 JSON 数组，带容错：
     * 先去除 Markdown 代码块围栏后整体解析；整体解析失败（如前后有杂文本）时，
     * 截取首个 [ 至最后一个 ] 再解析。若最终结果不是数组，视为格式异常。
     */
    private List<JsonNode> parseQuestionArray(String content) {
        String cleaned = cleanFence(content);
        JsonNode root;
        try {
            root = OBJECT_MAPPER.readTree(cleaned);
        } catch (JacksonException e) {
            String json = extractBrackets(cleaned);
            try {
                root = OBJECT_MAPPER.readTree(json);
            } catch (JacksonException e2) {
                throw new BusinessException("题目生成格式异常，请重试", e2);
            }
        }
        if (!root.isArray()) {
            throw new BusinessException("题目生成格式异常，请重试");
        }
        List<JsonNode> items = new ArrayList<>();
        root.forEach(items::add);
        return items;
    }

    /** 去除 Markdown 代码块围栏（```json / ```）并去除首尾空白 */
    private String cleanFence(String content) {
        if (content == null || content.isBlank()) {
            throw new BusinessException("题目生成格式异常，请重试");
        }
        String text = content.trim();
        if (text.startsWith("```")) {
            int firstNewline = text.indexOf('\n');
            if (firstNewline >= 0) {
                text = text.substring(firstNewline + 1);
            }
            if (text.endsWith("```")) {
                text = text.substring(0, text.length() - 3);
            }
        }
        return text.trim();
    }

    /** 截取首个 [ 至最后一个 ]，忽略前后杂文本 */
    private String extractBrackets(String text) {
        int start = text.indexOf('[');
        int end = text.lastIndexOf(']');
        if (start >= 0 && end > start) {
            return text.substring(start, end + 1);
        }
        return text;
    }

    /**
     * 将 LLM 返回的单个题目节点转换为实体。
     */
    private WrittenTestQuestion toQuestion(JsonNode item, Long sessionId, int orderNum) {
        WrittenTestQuestion question = new WrittenTestQuestion();
        question.setSessionId(sessionId);
        question.setType(item.path("type").asText());
        question.setContent(item.path("content").asText());
        question.setOptions(toJsonOrNull(item, "options"));
        question.setAnswer(textOrNull(item, "answer"));
        question.setExplanation(textOrNull(item, "explanation"));
        question.setKnowledgePoints(toJsonOrNull(item, "knowledgePoints"));
        question.setOrderNum(orderNum);
        return question;
    }

    /** 数组字段序列化为 JSON 字符串；缺失/null/空数组返回 null */
    private String toJsonOrNull(JsonNode item, String field) {
        JsonNode node = item.get(field);
        if (node == null || node.isNull() || !node.isArray() || node.isEmpty()) {
            return null;
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(node);
        } catch (JacksonException e) {
            throw new BusinessException("题目生成格式异常，请重试", e);
        }
    }

    /** 文本字段取非空值，缺失/null 时返回 null */
    private String textOrNull(JsonNode item, String field) {
        JsonNode node = item.get(field);
        if (node == null || node.isNull() || node.asText().isBlank()) {
            return null;
        }
        return node.asText();
    }

    private String valueOrDash(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }
}
