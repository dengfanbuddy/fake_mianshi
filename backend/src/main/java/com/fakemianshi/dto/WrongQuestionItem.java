package com.fakemianshi.dto;

import lombok.Data;

import java.util.List;

/**
 * 错题本里的一条错题。
 *
 * <p>注意这里**返回正确答案与解析**——和考试阶段的 {@link ExamQuestion} 相反。
 * 错题本就是拿来复盘和重练的，不给答案没法闭环。
 */
@Data
public class WrongQuestionItem {

    /** 作答记录 ID（重练时用来定位） */
    private Long answerId;

    private Long questionId;

    private Long sessionId;

    /** 题型 */
    private String type;

    /** 题干 */
    private String content;

    /** 选项 JSON 数组字符串（仅选择题） */
    private String options;

    /** 涉及知识点 */
    private List<String> knowledgePoints;

    /** 我当时的答案（选择题为字母，填空/简答为文本） */
    private String myAnswer;

    /** 参考答案 */
    private String correctAnswer;

    /** 答案解析 */
    private String explanation;

    /** 该题型能否自动判分（简答题为 false，只能对照解析自评） */
    private boolean autoGradable;

    /** 是否已订正 */
    private boolean corrected;

    /** 订正时间（ISO-8601 字符串，未订正为 null） */
    private String correctedAt;

    /** 重练次数 */
    private int retryCount;
}
