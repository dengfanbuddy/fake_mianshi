package com.fakemianshi.repository;

import com.fakemianshi.entity.PositionRequirement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * 岗位要求 Repository。
 */
public interface PositionRequirementRepository extends JpaRepository<PositionRequirement, Long> {

    /** 查询某项目下的全部岗位要求 */
    List<PositionRequirement> findByProjectId(Long projectId);
}
