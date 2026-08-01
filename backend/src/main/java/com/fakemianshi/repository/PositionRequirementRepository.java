package com.fakemianshi.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import com.fakemianshi.entity.PositionRequirement;

import java.util.List;

/**
 * 岗位要求 Repository。
 */
@Mapper
public interface PositionRequirementRepository extends BaseMapper<PositionRequirement> {

    /** 查询某项目下的全部岗位要求 */
    @Select("SELECT * FROM position_requirement WHERE project_id = #{projectId}")
    List<PositionRequirement> findByProjectId(Long projectId);
}
