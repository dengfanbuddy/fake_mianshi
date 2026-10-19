package com.fakemianshi.dto;

import lombok.Data;

/**
 * 错题本统计概览（按项目维度）。
 */
@Data
public class ReviewOverview {

    /** 错题总数（is_correct = 0 的笔试作答） */
    private int wrongTotal;

    /** 已订正数量 */
    private int correctedTotal;

    /** 待订正数量 = wrongTotal - correctedTotal */
    private int pendingTotal;

    /** 错题涉及的知识点数量（去重） */
    private int knowledgePointCount;

    /** 重练过至少一次的数量（用来看是否真的在复习） */
    private int retriedTotal;
}
