package com.fakemianshi.dto;

import lombok.Data;

/**
 * 错题重练结果。
 */
@Data
public class RetryAnswerResult {

    /** 是否判对（简答题恒为 false，看 autoGradable） */
    private boolean correct;

    /** 该题型能否自动判分 */
    private boolean autoGradable;

    /** 参考答案（无论对错都回传，便于当场对照） */
    private String correctAnswer;

    /** 答案解析 */
    private String explanation;

    /** 本次重练后的累计重练次数 */
    private int retryCount;

    /** 订正时间；本次刚订正成功时为新值，之前已订正则保持原值，未订正为 null */
    private String correctedAt;

    /** 是否本次才首次订正（用来给一次性的「订正成功」反馈） */
    private boolean firstCorrected;
}
