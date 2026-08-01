package com.fakemianshi.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import com.fakemianshi.entity.QuestionAnalysis;

import java.util.List;

/**
 * 单题分析 Repository。
 */
@Mapper
public interface QuestionAnalysisRepository extends BaseMapper<QuestionAnalysis> {

    /** 查询某会话下的全部单题分析 */
    @Select("SELECT * FROM question_analysis WHERE session_id = #{sessionId}")
    List<QuestionAnalysis> findBySessionId(Long sessionId);
}
