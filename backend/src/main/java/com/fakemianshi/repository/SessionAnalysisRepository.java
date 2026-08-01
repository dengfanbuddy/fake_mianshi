package com.fakemianshi.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import com.fakemianshi.entity.SessionAnalysis;

import java.util.List;

/**
 * 会话整体分析 Repository。
 */
@Mapper
public interface SessionAnalysisRepository extends BaseMapper<SessionAnalysis> {

    /** 查询某会话的分析记录 */
    @Select("SELECT * FROM session_analysis WHERE session_id = #{sessionId}")
    List<SessionAnalysis> findBySessionId(Long sessionId);
}
