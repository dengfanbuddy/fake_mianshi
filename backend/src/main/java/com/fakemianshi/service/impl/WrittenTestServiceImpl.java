package com.fakemianshi.service.impl;

import com.fakemianshi.config.BusinessException;
import com.fakemianshi.config.ResourceNotFoundException;
import com.fakemianshi.dto.ExamQuestion;
import com.fakemianshi.dto.QuestionResult;
import com.fakemianshi.dto.WrittenTestDetailResponse;
import com.fakemianshi.dto.WrittenTestStartRequest;
import com.fakemianshi.dto.WrittenTestStartResponse;
import com.fakemianshi.dto.WrittenTestSubmitRequest;
import com.fakemianshi.dto.WrittenTestSubmitResponse;
import com.fakemianshi.entity.InterviewSession;
import com.fakemianshi.entity.WrittenTestAnswer;
import com.fakemianshi.entity.WrittenTestQuestion;
import com.fakemianshi.repository.InterviewSessionRepository;
import com.fakemianshi.repository.WrittenTestAnswerRepository;
import com.fakemianshi.repository.WrittenTestQuestionRepository;
import com.fakemianshi.service.AnalysisService;
import com.fakemianshi.service.ProjectService;
import com.fakemianshi.service.QuestionGenerationService;
import com.fakemianshi.service.WrittenTestService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 笔试服务实现。
 *
 * <p>评分规则：单选/多选每题 3 分、填空每题 2 分、简答每题 5 分（暂不自动判分，留给 LLM 分析环节）；
 * 总分按 100 制归一化：实际得分 / 满分 * 100，保留 1 位小数。
 */
@Service
@RequiredArgsConstructor
public class WrittenTestServiceImpl implements WrittenTestService {

    private static final Logger log = LoggerFactory.getLogger(WrittenTestServiceImpl.class);

    private static final int DEFAULT_TIME_LIMIT = 60;
    private static final int DEFAULT_QUESTION_COUNT = 12;

    private static final String TYPE_SINGLE = "SINGLE_CHOICE";
    private static final String TYPE_MULTIPLE = "MULTIPLE_CHOICE";
    private static final String TYPE_FILL = "FILL_BLANK";
    private static final String TYPE_SHORT = "SHORT_ANSWER";

    private static final String SHORT_ANSWER_PENDING_HINT = "待AI分析";

    private final ProjectService projectService;
    private final InterviewSessionRepository sessionRepository;
    private final WrittenTestQuestionRepository questionRepository;
    private final WrittenTestAnswerRepository answerRepository;
    private final QuestionGenerationService questionGenerationService;
    private final AnalysisService analysisService;

    @Override
    @Transactional
    public WrittenTestStartResponse start(Long projectId, WrittenTestStartRequest req) {
        InterviewSession session = createSession(projectId, req);

        int questionCount = req != null && req.getQuestionCount() != null ? req.getQuestionCount() : DEFAULT_QUESTION_COUNT;
        List<WrittenTestQuestion> questions =
                questionGenerationService.generateWrittenTestQuestions(session.getId(), projectId, questionCount);
        return buildStartResponse(session, questions);
    }

    @Override
    @Transactional
    public WrittenTestStartResponse streamStart(Long projectId, WrittenTestStartRequest req,
                                                java.util.function.Consumer<String> onDelta) {
        InterviewSession session = createSession(projectId, req);

        int questionCount = req != null && req.getQuestionCount() != null ? req.getQuestionCount() : DEFAULT_QUESTION_COUNT;
        List<WrittenTestQuestion> questions = questionGenerationService
                .generateWrittenTestQuestionsStream(session.getId(), projectId, questionCount, onDelta);
        return buildStartResponse(session, questions);
    }

    /** 校验项目并创建笔试会话（含默认时长/题数） */
    private InterviewSession createSession(Long projectId, WrittenTestStartRequest req) {
        // 校验项目存在
        projectService.findById(projectId);

        int timeLimit = req != null && req.getTimeLimit() != null ? req.getTimeLimit() : DEFAULT_TIME_LIMIT;
        InterviewSession session = new InterviewSession();
        session.setProjectId(projectId);
        session.setType("WRITTEN");
        session.setStatus("IN_PROGRESS");
        session.setTimeLimit(timeLimit);
        session.setStartedAt(LocalDateTime.now());
        sessionRepository.insert(session);
        return session;
    }

    private WrittenTestStartResponse buildStartResponse(InterviewSession session, List<WrittenTestQuestion> questions) {
        List<ExamQuestion> examQuestions = questions.stream().map(this::toExamQuestion).toList();

        WrittenTestStartResponse response = new WrittenTestStartResponse();
        response.setSessionId(session.getId());
        response.setTimeLimit(session.getTimeLimit());
        response.setQuestions(examQuestions);
        return response;
    }

    @Override
    @Transactional
    public WrittenTestSubmitResponse submit(Long sessionId, WrittenTestSubmitRequest req) {
        InterviewSession session = Optional.ofNullable(sessionRepository.selectById(sessionId))
                .orElseThrow(() -> new BusinessException("笔试已提交或不存在"));
        if (!"IN_PROGRESS".equals(session.getStatus())) {
            throw new BusinessException("笔试已提交或不存在");
        }

        List<WrittenTestQuestion> questions = questionRepository.findBySessionIdOrderByOrderNum(sessionId);
        Map<Long, String> submitted = req != null && req.getAnswers() != null ? req.getAnswers() : Map.of();

        List<QuestionResult> results = new ArrayList<>();
        double earned = 0;
        double full = 0;
        int correctCount = 0;

        for (WrittenTestQuestion question : questions) {
            String type = question.getType();
            String userAnswer = submitted.get(question.getId());
            int weight = weightFor(type);
            full += weight;

            QuestionResult result = new QuestionResult();
            result.setQuestionId(question.getId());
            result.setType(type);
            result.setUserAnswer(userAnswer);
            result.setCorrectAnswer(question.getAnswer());

            WrittenTestAnswer answer = new WrittenTestAnswer();
            answer.setSessionId(sessionId);
            answer.setQuestionId(question.getId());
            answer.setUserAnswer(userAnswer);

            if (TYPE_SHORT.equals(type)) {
                // 简答题不做自动判分，isCorrect 置 null，留给 Task 19 的 LLM 分析环节评分
                result.setIsCorrect(null);
                result.setScore(0.0);
                result.setExplanation(SHORT_ANSWER_PENDING_HINT);
                answer.setIsCorrect(null);
                answer.setScore(0.0);
            } else {
                boolean correct = grade(question, userAnswer);
                result.setIsCorrect(correct);
                result.setScore(correct ? (double) weight : 0.0);
                result.setExplanation(question.getExplanation());
                answer.setIsCorrect(correct);
                answer.setScore(correct ? (double) weight : 0.0);
                if (correct) {
                    earned += weight;
                    correctCount++;
                }
            }
            answerRepository.insert(answer);
            results.add(result);
        }

        double totalScore = normalizeScore(earned, full);
        session.setStatus("COMPLETED");
        session.setCompletedAt(LocalDateTime.now());
        sessionRepository.updateById(session);

        // 自动触发会话分析（在事务提交后执行，失败不阻断提交）
        triggerAnalysisAfterCommit(sessionId);

        WrittenTestSubmitResponse response = new WrittenTestSubmitResponse();
        response.setSessionId(sessionId);
        response.setTotalScore(totalScore);
        response.setTotalQuestions(questions.size());
        response.setCorrectCount(correctCount);
        response.setResults(results);
        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public WrittenTestDetailResponse getDetail(Long sessionId) {
        InterviewSession session = Optional.ofNullable(sessionRepository.selectById(sessionId))
                .orElseThrow(() -> new ResourceNotFoundException("笔试会话不存在: id=" + sessionId));
        // 类型校验：模拟面试会话不应走笔试详情接口（前端以此判定会话类型）
        if (!"WRITTEN".equals(session.getType())) {
            throw new ResourceNotFoundException("该会话不是笔试会话");
        }

        List<WrittenTestQuestion> questions = questionRepository.findBySessionIdOrderByOrderNum(sessionId);
        Map<Long, WrittenTestAnswer> answerByQuestion = answerRepository.findBySessionId(sessionId).stream()
                .collect(Collectors.toMap(WrittenTestAnswer::getQuestionId, Function.identity(), (a, b) -> a));

        List<QuestionResult> results = new ArrayList<>();
        double earned = 0;
        double full = 0;

        for (WrittenTestQuestion question : questions) {
            int weight = weightFor(question.getType());
            full += weight;
            WrittenTestAnswer answer = answerByQuestion.get(question.getId());

            QuestionResult result = new QuestionResult();
            result.setQuestionId(question.getId());
            result.setType(question.getType());
            result.setUserAnswer(answer == null ? null : answer.getUserAnswer());
            result.setCorrectAnswer(question.getAnswer());
            result.setIsCorrect(answer == null ? null : answer.getIsCorrect());
            result.setScore(answer == null ? 0.0 : answer.getScore());
            result.setExplanation(question.getExplanation());

            if (answer != null && Boolean.TRUE.equals(answer.getIsCorrect())) {
                earned += answer.getScore() == null ? weight : answer.getScore();
            }
            results.add(result);
        }

        WrittenTestDetailResponse response = new WrittenTestDetailResponse();
        response.setSessionId(sessionId);
        response.setResults(results);
        response.setTotalScore(normalizeScore(earned, full));
        return response;
    }

    /**
     * 在事务提交后自动触发会话分析：保证分析能读取本次提交的全部作答数据。
     * 分析失败只记录日志，不阻断笔试提交；当前无活动事务（如单元测试）时直接调用。
     */
    private void triggerAnalysisAfterCommit(Long sessionId) {
        Runnable task = () -> {
            try {
                analysisService.analyzeSession(sessionId);
            } catch (Exception e) {
                log.warn("笔试会话分析自动触发失败: sessionId={}, cause={}", sessionId, e.getMessage());
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    task.run();
                }
            });
        } else {
            task.run();
        }
    }

    /** 题型权重：单选/多选 3 分，填空 2 分，简答 5 分 */
    private int weightFor(String type) {
        if (type == null) {
            return 0;
        }
        return switch (type) {
            case TYPE_SINGLE, TYPE_MULTIPLE -> 3;
            case TYPE_FILL -> 2;
            case TYPE_SHORT -> 5;
            default -> 0;
        };
    }

    /** 逐题判分（简答题不进入此流程） */
    private boolean grade(WrittenTestQuestion question, String userAnswer) {
        if (userAnswer == null || userAnswer.trim().isEmpty()) {
            return false;
        }
        String submitted = userAnswer.trim();
        String correct = question.getAnswer() == null ? "" : question.getAnswer().trim();
        return switch (question.getType() == null ? "" : question.getType()) {
            case TYPE_SINGLE -> submitted.equalsIgnoreCase(correct);
            case TYPE_MULTIPLE -> normalizeMultiple(submitted).equals(normalizeMultiple(correct));
            case TYPE_FILL -> submitted.equalsIgnoreCase(correct);
            default -> false;
        };
    }

    /** 多选答案规范化：去除空白/分隔符、转大写并按字母排序（"ACB"、"A,C,B" 均归为 "ABC"） */
    private String normalizeMultiple(String answer) {
        if (answer == null) {
            return "";
        }
        return answer.toUpperCase()
                .replaceAll("[^A-Z]", "")
                .chars()
                .sorted()
                .collect(StringBuilder::new, (sb, c) -> sb.append((char) c), StringBuilder::append)
                .toString();
    }

    /** 100 制归一化并保留 1 位小数 */
    private double normalizeScore(double earned, double full) {
        if (full <= 0) {
            return 0.0;
        }
        double raw = earned / full * 100;
        return BigDecimal.valueOf(raw).setScale(1, RoundingMode.HALF_UP).doubleValue();
    }

    /** 考试阶段的题目脱敏：不返回 answer / explanation */
    private ExamQuestion toExamQuestion(WrittenTestQuestion question) {
        ExamQuestion exam = new ExamQuestion();
        exam.setId(question.getId());
        exam.setType(question.getType());
        exam.setContent(question.getContent());
        exam.setOptions(question.getOptions());
        exam.setOrderNum(question.getOrderNum());
        return exam;
    }
}
