package com.fakemianshi.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import com.fakemianshi.entity.HistoricalAnalysis;

import java.util.List;

/**
 * 历史分析快照 Repository。
 */
@Mapper
public interface HistoricalAnalysisRepository extends BaseMapper<HistoricalAnalysis> {

    /** 查询某项目下的历史分析，按生成时间倒序 */
    @Select("SELECT * FROM historical_analysis WHERE project_id = #{projectId} ORDER BY generated_at DESC")
    List<HistoricalAnalysis> findByProjectIdOrderByGeneratedAtDesc(Long projectId);
}
