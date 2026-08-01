package com.fakemianshi.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import com.fakemianshi.entity.InterviewerPersona;

import java.util.List;

/**
 * 面试官人设 Repository。
 */
@Mapper
public interface InterviewerPersonaRepository extends BaseMapper<InterviewerPersona> {

    /** 按是否预置筛选 */
    @Select("SELECT * FROM interviewer_persona WHERE is_preset = #{isPreset}")
    List<InterviewerPersona> findByIsPreset(Boolean isPreset);
}
