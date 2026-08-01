package com.fakemianshi.repository;

import com.fakemianshi.entity.SessionAnalysis;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * 会话整体分析 Repository。
 */
public interface SessionAnalysisRepository extends JpaRepository<SessionAnalysis, Long> {

    /** 查询某会话的分析记录 */
    List<SessionAnalysis> findBySessionId(Long sessionId);
}
