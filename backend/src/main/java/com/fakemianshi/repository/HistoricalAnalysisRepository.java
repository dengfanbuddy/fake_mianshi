package com.fakemianshi.repository;

import com.fakemianshi.entity.HistoricalAnalysis;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * 历史分析快照 Repository。
 */
public interface HistoricalAnalysisRepository extends JpaRepository<HistoricalAnalysis, Long> {

    /** 查询某项目下的历史分析，按生成时间倒序 */
    List<HistoricalAnalysis> findByProjectIdOrderByGeneratedAtDesc(Long projectId);
}
