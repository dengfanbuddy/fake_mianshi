import io

# 1. 接口加两个方法
p = 'src/main/java/com/fakemianshi/service/AnalysisService.java'
src = io.open(p, encoding='utf-8').read()
old = '''    /**
     * 该会话的分析是否正在生成中（用于并发防重：避免重复触发导致 SQLite 写锁冲突）。
     */
    boolean isAnalyzing(Long sessionId);
}'''
new = '''    /**
     * 该会话的分析是否正在生成中（用于并发防重：避免重复触发导致 SQLite 写锁冲突）。
     */
    boolean isAnalyzing(Long sessionId);

    /**
     * 历史综合分析状态：已完成但未分析的会话数、是否存在比最新快照更新的分析结果等，
     * 用于前端提示"可重新综合分析 / 尚有会话未分析完"。
     */
    com.fakemianshi.dto.HistoryStatusDTO getHistoryStatus(Long projectId);

    /**
     * 强制重新生成历史综合分析：删除旧快照后重新调用 LLM 生成。
     */
    HistoricalAnalysis refreshHistory(Long projectId);
}'''
if old in src:
    src = src.replace(old, new, 1)
    io.open(p, 'w', encoding='utf-8', newline='').write(src)
    print('interface patched')
else:
    print('[WARN] interface anchor not found')

# 2. 实现
p = 'src/main/java/com/fakemianshi/service/impl/AnalysisServiceImpl.java'
src = io.open(p, encoding='utf-8').read()
anchor = '''    @Override
    @Transactional
    public void updateWeaknessTags(Long projectId, Long sessionId) {'''
add = '''    @Override
    @Transactional(readOnly = true)
    public com.fakemianshi.dto.HistoryStatusDTO getHistoryStatus(Long projectId) {
        List<InterviewSession> sessions = sessionRepository.findByProjectIdOrderByCreatedAtDesc(projectId);
        List<HistoricalAnalysis> snapshots =
                historicalAnalysisRepository.findByProjectIdOrderByGeneratedAtDesc(projectId);
        LocalDateTime latestHistoryAt = snapshots.isEmpty() ? null : snapshots.get(0).getGeneratedAt();

        int completed = 0;
        int unanalyzed = 0;
        LocalDateTime latestAnalyzedAt = null;
        for (InterviewSession s : sessions) {
            if (!"COMPLETED".equals(s.getStatus())) {
                continue;
            }
            completed++;
            List<SessionAnalysis> analyses = sessionAnalysisRepository.findBySessionId(s.getId());
            if (analyses.isEmpty()) {
                unanalyzed++;
            } else {
                LocalDateTime at = analyses.get(0).getCreatedAt();
                if (at != null && (latestAnalyzedAt == null || at.isAfter(latestAnalyzedAt))) {
                    latestAnalyzedAt = at;
                }
            }
        }

        com.fakemianshi.dto.HistoryStatusDTO dto = new com.fakemianshi.dto.HistoryStatusDTO();
        dto.setTotalSessions(sessions.size());
        dto.setCompletedSessions(completed);
        dto.setUnanalyzedCount(unanalyzed);
        dto.setLatestHistoryAt(latestHistoryAt);
        dto.setLatestAnalyzedAt(latestAnalyzedAt);
        // 有分析结果比快照新，或尚无快照但已有分析结果 → 需要（重新）综合分析
        boolean hasNew = latestAnalyzedAt != null
                && (latestHistoryAt == null || latestAnalyzedAt.isAfter(latestHistoryAt));
        dto.setHasNewAnalysis(hasNew);
        return dto;
    }

    @Override
    @Transactional
    public HistoricalAnalysis refreshHistory(Long projectId) {
        historicalAnalysisRepository.findByProjectIdOrderByGeneratedAtDesc(projectId)
                .forEach(h -> historicalAnalysisRepository.deleteById(h.getId()));
        return analyzeHistory(projectId);
    }

''' + anchor
if anchor in src:
    src = src.replace(anchor, add, 1)
    io.open(p, 'w', encoding='utf-8', newline='').write(src)
    print('impl patched')
else:
    print('[WARN] impl anchor not found')
