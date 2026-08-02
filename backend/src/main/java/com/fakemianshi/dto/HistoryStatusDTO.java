package com.fakemianshi.dto;

import java.time.LocalDateTime;

/**
 * 历史综合分析状态：用于提示"有新的分析结果可重新综合分析"或"存在未完成分析的会话"。
 */
public class HistoryStatusDTO {

    /** 项目总会话数 */
    private Integer totalSessions;

    /** 已完成（COMPLETED）会话数 */
    private Integer completedSessions;

    /** 已完成但尚未生成 AI 分析的会话数 */
    private Integer unanalyzedCount;

    /** 是否存在比最新历史快照更新的分析结果（需要重新综合分析） */
    private Boolean hasNewAnalysis;

    /** 最新历史综合分析快照生成时间（无则 null） */
    private LocalDateTime latestHistoryAt;

    /** 最新一次会话分析的生成时间（无则 null） */
    private LocalDateTime latestAnalyzedAt;

    public Integer getTotalSessions() {
        return totalSessions;
    }

    public void setTotalSessions(Integer totalSessions) {
        this.totalSessions = totalSessions;
    }

    public Integer getCompletedSessions() {
        return completedSessions;
    }

    public void setCompletedSessions(Integer completedSessions) {
        this.completedSessions = completedSessions;
    }

    public Integer getUnanalyzedCount() {
        return unanalyzedCount;
    }

    public void setUnanalyzedCount(Integer unanalyzedCount) {
        this.unanalyzedCount = unanalyzedCount;
    }

    public Boolean getHasNewAnalysis() {
        return hasNewAnalysis;
    }

    public void setHasNewAnalysis(Boolean hasNewAnalysis) {
        this.hasNewAnalysis = hasNewAnalysis;
    }

    public LocalDateTime getLatestHistoryAt() {
        return latestHistoryAt;
    }

    public void setLatestHistoryAt(LocalDateTime latestHistoryAt) {
        this.latestHistoryAt = latestHistoryAt;
    }

    public LocalDateTime getLatestAnalyzedAt() {
        return latestAnalyzedAt;
    }

    public void setLatestAnalyzedAt(LocalDateTime latestAnalyzedAt) {
        this.latestAnalyzedAt = latestAnalyzedAt;
    }
}
