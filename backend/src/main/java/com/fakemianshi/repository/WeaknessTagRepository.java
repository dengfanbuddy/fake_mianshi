package com.fakemianshi.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import com.fakemianshi.entity.WeaknessTag;

import java.util.List;

/**
 * 薄弱知识点标签 Repository。
 */
@Mapper
public interface WeaknessTagRepository extends BaseMapper<WeaknessTag> {

    /** 查询某项目下的全部弱点标签 */
    @Select("SELECT * FROM weakness_tag WHERE project_id = #{projectId}")
    List<WeaknessTag> findByProjectId(Long projectId);
}
