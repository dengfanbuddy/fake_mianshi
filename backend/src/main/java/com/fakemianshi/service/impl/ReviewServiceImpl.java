package com.fakemianshi.service.impl;

import com.fakemianshi.config.ResourceNotFoundException;
import com.fakemianshi.dto.RetryAnswerResult;
import com.fakemianshi.dto.ReviewOverview;
import com.fakemianshi.dto.WrongQuestionItem;
import com.fakemianshi.entity.InterviewSession;
import com.fakemianshi.entity.WeaknessTag;
import com.fakemianshi.entity.WrittenTestAnswer;
import com.fakemianshi.entity.WrittenTestQuestion;
import com.fakemianshi.repository.InterviewSessionRepository;
import com.fakemianshi.repository.WeaknessTagRepository;
import com.fakemianshi.repository.WrittenTestAnswerRepository;
import com.fakemianshi.repository.WrittenTestQuestionRepository;
import com.fakemianshi.service.ReviewService;
import com.fakemianshi.service.WeaknessTagService;
import com.fakemianshi.util.KnowledgePointNormalizer;
import com.fakemianshi.util.WrittenTestGrader;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 错题本实现：全部基于库里已有的作答记录，不调 LLM。
 *
 * <p>掌握程度回写规则（只在订正后往上调，不会因为重练答错而降低——答错本来就还是错题）：
 * <ul>
 *   <li>该知识点相关错题的订正比例 ≥ 80% → GOOD</li>
 *   <li>≥ 40% → FAIR</li>
 *   <li>否则不动</li>
 * </ul>
 * 只升不降，且只更新已存在的弱点标签（没被分析报告标记过的知识点不会凭空造一条）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewServiceImpl implements ReviewService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 掌握程度排序，用于「只升不降」判断 */
    private static final Map<String, Integer> MASTERY_RANK = Map.of(
            "WEAK", 0, "FAIR", 1, "GOOD", 2, "STRONG", 3);

    private static final String STATUS_PENDING = "pending";
    private static final String STATUS_CORRECTED = "corrected";

    private final WrittenTestAnswerRepository answerRepository;
    private final WrittenTestQuestionRepository questionRepository;
    private final InterviewSessionRepository sessionRepository;
    private final WeaknessTagRepository weaknessTagRepository;
    private final WeaknessTagService weaknessTagService;

    /** 作答 + 题目（错题本的所有展示与统计都需要两者） */
    private record WrongRow(WrittenTestAnswer answer, WrittenTestQuestion question) {
    }

    @Override
    @Transactional(readOnly = true)
    public ReviewOverview overview(Long projectId) {
        List<WrongRow> rows = loadWrongRows(projectId);
        ReviewOverview overview = new ReviewOverview();
        overview.setWrongTotal(rows.size());
        int corrected = (int) rows.stream().filter(r -> r.answer().getCorrectedAt() != null).count();
        overview.setCorrectedTotal(corrected);
        overview.setPendingTotal(rows.size() - corrected);
        overview.setRetriedTotal((int) rows.stream()
                .filter(r -> r.answer().getRetryCount() != null && r.answer().getRetryCount() > 0)
                .count());
        Set<String> points = new LinkedHashSet<>();
        for (WrongRow row : rows) {
            points.addAll(parseStringList(row.question().getKnowledgePoints()));
        }
        overview.setKnowledgePointCount(points.size());
        return overview;
    }

    @Override
    @Transactional(readOnly = true)
    public List<WrongQuestionItem> listWrongQuestions(Long projectId, String status, String knowledgePoint, Long tagId) {
        List<WrongRow> rows = loadWrongRows(projectId);
        List<String> wantedPoints = resolveMatchPoints(projectId, tagId, knowledgePoint);
        List<WrongQuestionItem> items = new ArrayList<>(rows.size());
        for (WrongRow row : rows) {
            boolean corrected = row.answer().getCorrectedAt() != null;
            if (STATUS_PENDING.equals(status) && corrected) {
                continue;
            }
            if (STATUS_CORRECTED.equals(status) && !corrected) {
                continue;
            }
            List<String> points = parseStringList(row.question().getKnowledgePoints());
            if (wantedPoints != null && !matchesAny(points, wantedPoints)) {
                continue;
            }
            items.add(toItem(row, points, corrected));
        }
        return items;
    }

    @Override
    @Transactional
    public RetryAnswerResult retry(Long questionId, String userAnswer) {
        WrittenTestQuestion question = questionRepository.selectById(questionId);
        if (question == null) {
            throw new ResourceNotFoundException("题目不存在: id=" + questionId);
        }
        WrittenTestAnswer answer = answerRepository.findByQuestionId(questionId);
        if (answer == null) {
            throw new ResourceNotFoundException("该题没有作答记录，无法重练: id=" + questionId);
        }

        boolean autoGradable = WrittenTestGrader.autoGradable(question.getType());
        // 简答题无法自动判分：grade 恒为 false，前端据此提示「请对照解析自评」
        boolean correct = WrittenTestGrader.grade(question.getType(), question.getAnswer(), userAnswer);

        int retryCount = (answer.getRetryCount() == null ? 0 : answer.getRetryCount()) + 1;
        answer.setRetryCount(retryCount);
        boolean firstCorrected = false;
        if (correct && answer.getCorrectedAt() == null) {
            answer.setCorrectedAt(LocalDateTime.now().toString());
            firstCorrected = true;
        }
        answerRepository.updateById(answer);

        if (correct) {
            improveMastery(answer.getSessionId(), question.getKnowledgePoints());
        }

        RetryAnswerResult result = new RetryAnswerResult();
        result.setCorrect(correct);
        result.setAutoGradable(autoGradable);
        result.setCorrectAnswer(question.getAnswer());
        result.setExplanation(question.getExplanation());
        result.setRetryCount(retryCount);
        result.setCorrectedAt(answer.getCorrectedAt());
        result.setFirstCorrected(firstCorrected);
        return result;
    }

    // ---------------- 内部 ----------------

    /**
     * 把「按哪个知识点筛」翻译成一组可用于匹配的写法。
     *
     * <p>必须按标签的全部同义写法匹配，不能只拿代表写法做子串比较：题目上的知识点是 LLM
     * 生成题目时写的原始写法，与标签合并后保留的代表写法十有八九不一致，
     * 只比代表写法会让「点知识点筛选 → 一条都不剩」。
     *
     * @return null 表示不筛选；空列表表示筛了但筛不到任何东西（如标签已被归并删除）
     */
    private List<String> resolveMatchPoints(Long projectId, Long tagId, String knowledgePoint) {
        if (tagId != null) {
            WeaknessTag tag = weaknessTagRepository.selectById(tagId);
            if (tag == null || (projectId != null && !projectId.equals(tag.getProjectId()))) {
                // 标签已不存在（可能刚被归并掉）或不属于该项目：明确筛空，
                // 而不是悄悄忽略筛选条件把全部错题都端出来
                return List.of();
            }
            List<String> points = new ArrayList<>();
            if (tag.getKnowledgePoint() != null && !tag.getKnowledgePoint().isBlank()) {
                points.add(tag.getKnowledgePoint());
            }
            points.addAll(weaknessTagService.aliasesOf(tag));
            return points;
        }
        if (knowledgePoint != null && !knowledgePoint.isBlank()) {
            return List.of(knowledgePoint.trim());
        }
        return null;
    }

    /** 题目的任一知识点与待筛写法同义（或互相包含）即算命中 */
    private boolean matchesAny(List<String> questionPoints, List<String> wanted) {
        for (String point : questionPoints) {
            for (String target : wanted) {
                if (KnowledgePointNormalizer.sameTopic(point, target)) {
                    return true;
                }
                String a = point.trim();
                String b = target.trim();
                if (a.length() >= 4 && b.length() >= 4 && (a.contains(b) || b.contains(a))) {
                    return true;
                }
            }
        }
        return false;
    }

    private WrongQuestionItem toItem(WrongRow row, List<String> points, boolean corrected) {
        WrongQuestionItem item = new WrongQuestionItem();
        item.setAnswerId(row.answer().getId());
        item.setQuestionId(row.question().getId());
        item.setSessionId(row.answer().getSessionId());
        item.setType(row.question().getType());
        item.setContent(row.question().getContent());
        item.setOptions(row.question().getOptions());
        item.setKnowledgePoints(points);
        item.setMyAnswer(row.answer().getUserAnswer());
        item.setCorrectAnswer(row.question().getAnswer());
        item.setExplanation(row.question().getExplanation());
        item.setAutoGradable(WrittenTestGrader.autoGradable(row.question().getType()));
        item.setCorrected(corrected);
        item.setCorrectedAt(row.answer().getCorrectedAt());
        item.setRetryCount(row.answer().getRetryCount() == null ? 0 : row.answer().getRetryCount());
        return item;
    }

    private List<WrongRow> loadWrongRows(Long projectId) {
        if (projectId == null) {
            return List.of();
        }
        List<WrittenTestAnswer> answers = answerRepository.findWrongByProjectId(projectId);
        List<WrongRow> rows = new ArrayList<>(answers.size());
        for (WrittenTestAnswer answer : answers) {
            WrittenTestQuestion question = questionRepository.selectById(answer.getQuestionId());
            // 题目被删但作答还在：跳过而不是抛错，错题本不能因为一条脏数据整页打不开
            if (question == null) {
                continue;
            }
            rows.add(new WrongRow(answer, question));
        }
        return rows;
    }

    /**
     * 订正后上调掌握程度（只升不降，且只更新已存在的弱点标签）。
     *
     * @param sessionId         刚订正的那道题所属会话（用来定位项目）
     * @param knowledgePointsJson 该题的知识点 JSON 数组
     */
    private void improveMastery(Long sessionId, String knowledgePointsJson) {
        List<String> points = parseStringList(knowledgePointsJson);
        if (points.isEmpty() || sessionId == null) {
            return;
        }
        InterviewSession session = sessionRepository.selectById(sessionId);
        if (session == null || session.getProjectId() == null) {
            return;
        }
        Long projectId = session.getProjectId();
        // 重新读一遍：刚才的 updateById 已经把 correctedAt 落库，这里的比例才是最新的
        List<WrongRow> allRows = loadWrongRows(projectId);
        List<WeaknessTag> tags = weaknessTagRepository.findByProjectId(projectId);
        if (tags == null || tags.isEmpty()) {
            return;
        }
        for (String point : points) {
            List<WrongRow> related = allRows.stream()
                    .filter(r -> parseStringList(r.question().getKnowledgePoints()).contains(point))
                    .toList();
            if (related.isEmpty()) {
                continue;
            }
            long corrected = related.stream().filter(r -> r.answer().getCorrectedAt() != null).count();
            double ratio = corrected / (double) related.size();
            String target = ratio >= 0.8 ? "GOOD" : ratio >= 0.4 ? "FAIR" : null;
            if (target == null) {
                continue;
            }
            WeaknessTag tag = weaknessTagService.findSameTopic(tags, point);
            if (tag == null) {
                continue;
            }
            int current = MASTERY_RANK.getOrDefault(tag.getMasteryLevel(), 0);
            int wanted = MASTERY_RANK.getOrDefault(target, 0);
            if (wanted <= current) {
                continue;
            }
            String before = tag.getMasteryLevel();
            tag.setMasteryLevel(target);
            tag.setUpdatedAt(LocalDateTime.now());
            weaknessTagRepository.updateById(tag);
            log.info("错题订正后上调掌握程度: {} {} -> {}（订正 {}/{}）",
                    point, before, target, corrected, related.size());
        }
    }

    /** 知识点存的是 JSON 数组字符串；脏数据按空处理，绝不因为解析失败让整页报错 */
    private List<String> parseStringList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            JsonNode node = MAPPER.readTree(json);
            if (node == null || !node.isArray()) {
                return List.of();
            }
            List<String> list = new ArrayList<>(node.size());
            for (JsonNode item : node) {
                String text = item.asText("").trim();
                if (!text.isEmpty()) {
                    list.add(text);
                }
            }
            return list;
        } catch (JacksonException e) {
            return List.of();
        }
    }

    /** 供控制器做参数校验时复用的状态白名单 */
    public static boolean isValidStatus(String status) {
        return status == null || status.isBlank()
                || STATUS_PENDING.equals(status) || STATUS_CORRECTED.equals(status);
    }
}
