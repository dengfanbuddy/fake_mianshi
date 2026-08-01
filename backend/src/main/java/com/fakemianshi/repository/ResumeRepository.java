package com.fakemianshi.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import com.fakemianshi.entity.Resume;

import java.util.List;

/**
 * 简历 Repository。
 */
@Mapper
public interface ResumeRepository extends BaseMapper<Resume> {

    /** 查询某项目下的全部简历 */
    @Select("SELECT * FROM resume WHERE project_id = #{projectId}")
    List<Resume> findByProjectId(Long projectId);
}
