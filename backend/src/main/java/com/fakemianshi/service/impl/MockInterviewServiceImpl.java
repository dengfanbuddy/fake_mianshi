package com.fakemianshi.service.impl;

import com.fakemianshi.config.BusinessException;
import com.fakemianshi.dto.LlmResponse;
import com.fakemianshi.dto.MockInterviewRespondRequest;
import com.fakemianshi.dto.MockInterviewRespondResponse;
import com.fakemianshi.dto.MockInterviewStartRequest;
import com.fakemianshi.dto.MockInterviewStartResponse;
import com.fakemianshi.entity.InterviewSession;
import com.fakemianshi.entity.InterviewerPersona;
import com.fakemianshi.entity.MockInterviewMessage;
import com.fakemianshi.entity.PositionRequirement;
import com.fakemianshi.entity.Resume;
import com.fakemianshi.entity.WeaknessTag;
import com.fakemianshi.entity.WrittenTestAnswer;
import com.fakemianshi.entity.WrittenTestQuestion;
import com.fakemianshi.repository.InterviewSessionRepository;
import com.fakemianshi.repository.MockInterviewMessageRepository;
import com.fakemianshi.repository.WeaknessTagRepository;
import com.fakemianshi.repository.WrittenTestAnswerRepository;
import com.fakemianshi.repository.WrittenTestQuestionRepository;
import com.fakemianshi.service.LlmService;
import com.fakemianshi.service.MockInterviewService;
import com.fakemianshi.service.PersonaService;
import com.fakemianshi.service.PositionRequirementService;
import com.fakemianshi.service.ProjectService;
import com.fakemianshi.service.QuestionGenerationService;
import com.fakemianshi.service.ResumeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 模拟面试服务实现。
 *
 * <p>核心机制：AI 面试官根据候选人实时回答动态决定下一问，每轮把完整对话历史
 * （system + 历史 user/assistant 消息）发给 LLM，保证追问连续、风格一致。
 *
 * <p>MVP 简化说明：
 * <ul>
 *   <li>面试大纲不落库，按会话缓存在内存，供多轮对话复用，服务重启后自动重新生成；</li>
 *   <li>当前面试官人设通过「最新一条 INTERVIEWER 消息的 personaId」推导，
 *       因此 switchPersona 只需插入一条换人消息即可无缝切换；</li>
 *   <li>referenceWrittenTest 标记按会话缓存在内存，配合「项目最近存在 COMPLETED 笔试」
 *       的 OR 条件决定是否把笔试错误题知识点加入面试官考察重点。</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class MockInterviewServiceImpl implements MockInterviewService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /** 简历原文截断长度（无分析结果时取前 2000 字） */
    private static final int RESUME_PARSED_TEXT_LIMIT = 2000;

    private static final String ROLE_INTERVIEWER = "INTERVIEWER";
    private static final String ROLE_CANDIDATE = "CANDIDATE";
    private static final String STATUS_IN_PROGRESS = "IN_PROGRESS";
    private static final String STATUS_COMPLETED = "COMPLETED";
    private static final String TYPE_MOCK = "MOCK";
    private static final String TYPE_WRITTEN = "WRITTEN";

    private final ProjectService projectService;
    private final PersonaService personaService;
    private final PositionRequirementService positionRequirementService;
    private final ResumeService resumeService;
    private final InterviewSessionRepository sessionRepository;
    private final MockInterviewMessageRepository messageRepository;
    private final WrittenTestAnswerRepository writtenTestAnswerRepository;
    private final WrittenTestQuestionRepository writtenTestQuestionRepository;
    private final WeaknessTagRepository weaknessTagRepository;
    private final QuestionGenerationService questionGenerationService;
    private final LlmService llmService;

    /** 会话 -> 面试大纲（JSON），MVP 不持久化 */
    private final ConcurrentHashMap<Long, String> outlineCache = new ConcurrentHashMap<>();

    /** 会话 -> 是否引用笔试结果标记（来自 start 请求，MVP 不持久化） */
    private final ConcurrentHashMap<Long, Boolean> referenceWrittenTestFlags = new ConcurrentHashMap<>();

    @Override
    @Transactional
    public MockInterviewStartResponse start(Long projectId, MockInterviewStartRequest req) {
        // 1. 校验项目存在
        projectService.findById(projectId);

        // 2. 解析人设：指定则校验存在，否则取第一个预设
        InterviewerPersona persona = resolveStartPersona(req);

        // 3. 创建 MOCK 会话
        InterviewSession session = new InterviewSession();
        session.setProjectId(projectId);
        session.setType(TYPE_MOCK);
        session.setStatus(STATUS_IN_PROGRESS);
        session.setStartedAt(LocalDateTime.now());
        PositionRequirement positionRequirement = positionRequirementService.getByProjectId(projectId);
        session.setPositionRequirementId(positionRequirement == null ? null : positionRequirement.getId());
        sessionRepository.save(session);

        // 4. 生成开场白并作为第一条 INTERVIEWER 消息保存
        String openingMessage = questionGenerationService.generateOpeningMessage(projectId, persona.getId());
        MockInterviewMessage opening = new MockInterviewMessage();
        opening.setSessionId(session.getId());
        opening.setRole(ROLE_INTERVIEWER);
        opening.setContent(openingMessage);
        opening.setPersonaId(persona.getId());
        messageRepository.save(opening);

        // 5. 生成面试大纲（MVP 不持久化，缓存后放响应返回）
        String outline = questionGenerationService.generateMockInterviewOutline(projectId);
        outlineCache.put(session.getId(), outline);

        boolean referenceWrittenTest = req != null && Boolean.TRUE.equals(req.getReferenceWrittenTest());
        referenceWrittenTestFlags.put(session.getId(), referenceWrittenTest);

        // 6. 组装响应
        MockInterviewStartResponse response = new MockInterviewStartResponse();
        response.setSessionId(session.getId());
        response.setPersonaId(persona.getId());
        response.setPersonaName(persona.getName());
        response.setOpeningMessage(openingMessage);
        response.setOutline(outline);
        return response;
    }

    @Override
    @Transactional
    public MockInterviewRespondResponse respond(Long sessionId, MockInterviewRespondRequest req) {
        // 1. 校验会话存在且进行中
        InterviewSession session = requireActiveSession(sessionId);
        if (req == null || req.getUserText() == null || req.getUserText().isBlank()) {
            throw new BusinessException("回答内容不能为空");
        }

        // 2. 保存候选人消息
        MockInterviewMessage userMessage = new MockInterviewMessage();
        userMessage.setSessionId(sessionId);
        userMessage.setRole(ROLE_CANDIDATE);
        userMessage.setContent(req.getUserText());
        userMessage.setAudioPath(req.getAudioPath());
        messageRepository.save(userMessage);
        messageRepository.flush();

        // 3. 确定当前面试官人设（最新一条 INTERVIEWER 消息的 personaId）
        InterviewerPersona persona = resolveCurrentPersona(sessionId);

        // 4. 构建 LLM 对话：system + 完整历史
        String outline = getOutline(sessionId, session.getProjectId());
        List<LlmService.Message> messages = buildMessages(session, persona, outline, null);

        LlmResponse llmResponse = llmService.chat(messages);
        String replyContent = llmResponse.getContent() == null ? "" : llmResponse.getContent();

        // 5. 保存面试官消息
        MockInterviewMessage aiMessage = new MockInterviewMessage();
        aiMessage.setSessionId(sessionId);
        aiMessage.setRole(ROLE_INTERVIEWER);
        aiMessage.setContent(replyContent);
        aiMessage.setPersonaId(persona.getId());
        messageRepository.save(aiMessage);

        MockInterviewRespondResponse response = new MockInterviewRespondResponse();
        response.setUserMessage(userMessage);
        response.setAiMessage(aiMessage);
        return response;
    }

    @Override
    @Transactional
    public MockInterviewMessage concludeInterview(Long sessionId) {
        // 1. 校验会话存在且进行中
        InterviewSession session = requireActiveSession(sessionId);

        InterviewerPersona persona = resolveCurrentPersona(sessionId);
        String outline = getOutline(sessionId, session.getProjectId());

        // 2. 构造收尾 prompt：system 同上，最后追加一条 user 消息要求总结
        String userPrompt = "请总结本次面试，给出候选人的整体表现评价和后续建议，然后宣布面试结束。";
        List<LlmService.Message> messages = buildMessages(session, persona, outline, userPrompt);

        LlmResponse llmResponse = llmService.chat(messages);
        String content = llmResponse.getContent() == null ? "" : llmResponse.getContent();

        // 3. 保存面试官收尾消息
        MockInterviewMessage summaryMessage = new MockInterviewMessage();
        summaryMessage.setSessionId(sessionId);
        summaryMessage.setRole(ROLE_INTERVIEWER);
        summaryMessage.setContent(content);
        summaryMessage.setPersonaId(persona.getId());
        messageRepository.save(summaryMessage);

        // 4. 会话置为 COMPLETED
        session.setStatus(STATUS_COMPLETED);
        session.setCompletedAt(LocalDateTime.now());
        sessionRepository.save(session);

        return summaryMessage;
    }

    @Override
    @Transactional(readOnly = true)
    public List<MockInterviewMessage> getMessages(Long sessionId) {
        return messageRepository.findBySessionIdOrderByCreatedAt(sessionId);
    }

    @Override
    @Transactional
    public void switchPersona(Long sessionId, Long newPersonaId) {
        sessionRepository.findById(sessionId)
                .orElseThrow(() -> new BusinessException("模拟面试会话不存在: id=" + sessionId));
        InterviewerPersona persona = personaService.findById(newPersonaId);

        MockInterviewMessage note = new MockInterviewMessage();
        note.setSessionId(sessionId);
        note.setRole(ROLE_INTERVIEWER);
        note.setContent("面试官已更换为【" + persona.getName() + "】，接下来将按照新面试官的风格提问。");
        note.setPersonaId(persona.getId());
        messageRepository.save(note);
    }

    // ---------------- 内部方法 ----------------

    /**
     * 组装发给 LLM 的完整消息列表：system 首条 + 会话历史（CANDIDATE→user，INTERVIEWER→assistant），
     * 可选地在末尾追加一条 user 消息（用于收尾总结）。
     */
    private List<LlmService.Message> buildMessages(InterviewSession session,
                                                   InterviewerPersona persona,
                                                   String outline,
                                                   String trailingUserContent) {
        List<LlmService.Message> messages = new ArrayList<>();
        messages.add(new LlmService.Message("system", buildInterviewerSystemPrompt(session, persona, outline)));

        for (MockInterviewMessage m : messageRepository.findBySessionIdOrderByCreatedAt(session.getId())) {
            String role = ROLE_CANDIDATE.equals(m.getRole()) ? "user" : "assistant";
            messages.add(new LlmService.Message(role, m.getContent()));
        }

        if (trailingUserContent != null && !trailingUserContent.isBlank()) {
            messages.add(new LlmService.Message("user", trailingUserContent));
        }
        return messages;
    }

    /**
     * 组装面试官 system prompt：人设身份与追问策略、面试大纲、简历摘要、弱点标签、最近笔试情况。
     */
    private String buildInterviewerSystemPrompt(InterviewSession session,
                                                InterviewerPersona persona,
                                                String outline) {
        Resume resume = resumeService.getLatestByProjectId(session.getProjectId());
        List<WeaknessTag> tags = weaknessTagRepository.findByProjectId(session.getProjectId());
        String writtenTestContext = buildWrittenTestContext(session);

        String writtenSection = writtenTestContext == null ? "" : writtenTestContext;

        return """
                你是一位资深技术面试官，正在对候选人进行一轮模拟面试，请以真实面试的节奏推进对话。

                【面试官身份】
                名称：%s
                风格描述：%s
                %s

                【面试大纲】
                本次模拟面试的大纲如下（JSON 文本），请据此安排提问节奏与覆盖范围：
                %s

                【候选人简历】
                %s

                【候选人弱点标签】
                %s

                %s

                【对话规则】
                1. 面试问题必须在面试过程中实时生成，根据候选人上一条回答动态决定下一步：可追问深挖、转换话题，或在纠正错误后继续，不要提前把所有问题一次抛出；
                2. 当候选人回答错误或含糊不清时，必须追问澄清或给出提示后再继续，不要直接跳过；
                3. 面试不设固定时限，由面试官根据题量与对话轮数自然收尾；当大纲主题已覆盖或对话轮数足够时，自然结束面试并给出简短总结，同时明确告诉候选人面试结束，收尾消息必须以“【面试结束】”开头；
                4. 每一轮只提出一个问题或一段追问，不要一次抛出多个问题；
                5. 始终保持本面试官的风格（用词、语气、追问策略），不要偏离身份。

                现在，请结合以上信息与对话历史，继续本次面试。
                """.formatted(
                persona.getName() == null ? "-" : persona.getName(),
                persona.getDescription() == null ? "-" : persona.getDescription(),
                buildPersonaStyleText(persona),
                outline == null ? "（无）" : outline,
                buildResumeText(resume),
                buildWeaknessText(tags),
                writtenSection);
    }

    /**
     * 解析人设 styleConfig（JSON），提取追问策略与语气，转成 prompt 友好的文字描述。
     */
    private String buildPersonaStyleText(InterviewerPersona persona) {
        String styleConfig = persona.getStyleConfig();
        if (styleConfig == null || styleConfig.isBlank()) {
            return "";
        }
        try {
            JsonNode node = OBJECT_MAPPER.readTree(styleConfig);
            String followup = node.path("followupStrategy").asText("");
            String tone = node.path("tone").asText("");
            String followupDesc = switch (followup) {
                case "deep_dive" -> "不断追问底层原理和实现细节，刨根问底到源码级别";
                case "challenge" -> "质疑回答、施加压力，重点考察抗压能力";
                case "guide" -> "友好引导，答不上来时给提示并换角度提问";
                case "project_drill" -> "围绕简历项目深挖架构决策、踩坑经验与技术选型理由";
                case "checklist" -> "按知识点清单逐个考察，不追问过深";
                default -> followup.isBlank() ? "根据回答动态深挖" : followup;
            };
            StringBuilder sb = new StringBuilder("追问策略：").append(followupDesc);
            if (!tone.isBlank()) {
                sb.append("；语气：").append(tone);
            }
            return sb.toString();
        } catch (JacksonException e) {
            // styleConfig 非 JSON 时降级为通用策略
            return "追问策略：根据回答动态深挖";
        }
    }

    /**
     * 组装候选人简历摘要：优先用 LLM 分析结果，否则取原文前 2000 字。
     */
    private String buildResumeText(Resume resume) {
        if (resume == null) {
            return "（无简历）";
        }
        if (resume.getAnalysisResult() != null && !resume.getAnalysisResult().isBlank()) {
            return "简历分析结果：\n" + resume.getAnalysisResult();
        }
        String parsed = resume.getParsedText();
        if (parsed == null || parsed.isBlank()) {
            return "（无简历内容）";
        }
        if (parsed.length() > RESUME_PARSED_TEXT_LIMIT) {
            parsed = parsed.substring(0, RESUME_PARSED_TEXT_LIMIT);
        }
        return "简历原文：\n" + parsed;
    }

    /**
     * 组装弱点标签文本。
     */
    private String buildWeaknessText(List<WeaknessTag> tags) {
        if (tags == null || tags.isEmpty()) {
            return "（无）";
        }
        StringBuilder sb = new StringBuilder("以下知识点候选人掌握较弱，面试时应优先考察：\n");
        for (WeaknessTag tag : tags) {
            sb.append("- ")
                    .append(tag.getKnowledgePoint() == null ? "-" : tag.getKnowledgePoint())
                    .append("（掌握程度：")
                    .append(tag.getMasteryLevel() == null ? "-" : tag.getMasteryLevel())
                    .append("）\n");
        }
        return sb.toString();
    }

    /**
     * 最近笔试情况：若本会话引用笔试结果（start 时 referenceWrittenTest）或
     * 该项目最近存在 COMPLETED 笔试，则把笔试错误题知识点纳入「重点考察」。
     */
    private String buildWrittenTestContext(InterviewSession session) {
        List<InterviewSession> writtenSessions = sessionRepository
                .findByProjectIdAndTypeAndStatusOrderByCompletedAtDesc(
                        session.getProjectId(), TYPE_WRITTEN, STATUS_COMPLETED);

        boolean include = Boolean.TRUE.equals(referenceWrittenTestFlags.get(session.getId()))
                || !writtenSessions.isEmpty();
        if (!include || writtenSessions.isEmpty()) {
            return null;
        }

        InterviewSession writtenSession = writtenSessions.get(0);
        Map<Long, WrittenTestQuestion> questionById = writtenTestQuestionRepository
                .findBySessionIdOrderByOrderNum(writtenSession.getId()).stream()
                .collect(Collectors.toMap(WrittenTestQuestion::getId, Function.identity(), (a, b) -> a));

        List<WrittenTestAnswer> wrongAnswers = writtenTestAnswerRepository.findBySessionId(writtenSession.getId())
                .stream()
                .filter(a -> Boolean.FALSE.equals(a.getIsCorrect()))
                .toList();

        StringBuilder sb = new StringBuilder("【最近笔试情况】\n");
        sb.append("候选人在最近一次笔试中答错的题目及其知识点，属于重点考察范围：\n");
        if (wrongAnswers.isEmpty()) {
            sb.append("（最近笔试无自动判分的错误记录）\n");
            return sb.toString();
        }
        for (WrittenTestAnswer answer : wrongAnswers) {
            WrittenTestQuestion question = questionById.get(answer.getQuestionId());
            if (question == null) {
                continue;
            }
            sb.append("- 题目：").append(question.getContent() == null ? "-" : question.getContent()).append('\n');
            sb.append("  知识点：").append(parseKnowledgePoints(question.getKnowledgePoints())).append('\n');
        }
        return sb.toString();
    }

    /**
     * 把笔试知识点的 JSON 数组解析为顿号分隔文本；解析失败或缺失时原样返回。
     */
    private String parseKnowledgePoints(String knowledgePointsJson) {
        if (knowledgePointsJson == null || knowledgePointsJson.isBlank()) {
            return "-";
        }
        try {
            JsonNode node = OBJECT_MAPPER.readTree(knowledgePointsJson);
            if (node.isArray()) {
                List<String> points = new ArrayList<>();
                node.forEach(n -> {
                    if (n.isTextual()) {
                        points.add(n.asText());
                    }
                });
                return points.isEmpty() ? "-" : String.join("、", points);
            }
            return knowledgePointsJson;
        } catch (JacksonException e) {
            return knowledgePointsJson;
        }
    }

    /**
     * 取会话大纲：优先走内存缓存，缺省时重新调用出题引擎生成（如服务重启后）。
     */
    private String getOutline(Long sessionId, Long projectId) {
        return outlineCache.computeIfAbsent(sessionId,
                id -> questionGenerationService.generateMockInterviewOutline(projectId));
    }

    /**
     * 校验会话存在且为 IN_PROGRESS，否则抛业务异常。
     */
    private InterviewSession requireActiveSession(Long sessionId) {
        InterviewSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new BusinessException("模拟面试会话不存在: id=" + sessionId));
        if (!STATUS_IN_PROGRESS.equals(session.getStatus())) {
            throw new BusinessException("当前面试会话已结束或不可继续，请重新开始");
        }
        return session;
    }

    /**
     * 推导当前面试官人设：取该会话最新一条带 personaId 的 INTERVIEWER 消息。
     * 由此实现中途换人（switchPersona）后，后续回复自动使用新人设。
     */
    private InterviewerPersona resolveCurrentPersona(Long sessionId) {
        List<MockInterviewMessage> messages = messageRepository.findBySessionIdOrderByCreatedAt(sessionId);
        Long personaId = null;
        for (int i = messages.size() - 1; i >= 0; i--) {
            MockInterviewMessage m = messages.get(i);
            if (ROLE_INTERVIEWER.equals(m.getRole()) && m.getPersonaId() != null) {
                personaId = m.getPersonaId();
                break;
            }
        }
        if (personaId == null) {
            throw new BusinessException("面试官人设信息缺失，无法继续面试");
        }
        return personaService.findById(personaId);
    }

    /**
     * 解析开始面试的人设：请求指定则 findById 校验，否则取第一个预设。
     */
    private InterviewerPersona resolveStartPersona(MockInterviewStartRequest req) {
        if (req != null && req.getPersonaId() != null) {
            return personaService.findById(req.getPersonaId());
        }
        List<InterviewerPersona> personas = personaService.findAll();
        if (personas.isEmpty()) {
            throw new BusinessException("暂无可用面试官人设，请先配置");
        }
        return personas.get(0);
    }
}
