package com.fakemianshi.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import com.fakemianshi.entity.InterviewProject;

import java.util.List;

/**
 * 面试项目 Repository。
 */
@Mapper
public interface InterviewProjectRepository extends BaseMapper<InterviewProject> {

    /** 按创建时间倒序查询全部项目 */
    @Select("SELECT * FROM interview_project ORDER BY created_at DESC")
    List<InterviewProject> findAllByOrderByCreatedAtDesc();
}
