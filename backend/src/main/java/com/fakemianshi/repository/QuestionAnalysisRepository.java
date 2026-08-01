package com.fakemianshi.repository;

import com.fakemianshi.entity.QuestionAnalysis;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * 单题分析 Repository。
 */
public interface QuestionAnalysisRepository extends JpaRepository<QuestionAnalysis, Long> {

    /** 查询某会话下的全部单题分析 */
    List<QuestionAnalysis> findBySessionId(Long sessionId);
}
