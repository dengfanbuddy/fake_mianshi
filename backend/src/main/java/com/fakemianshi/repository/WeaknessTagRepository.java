package com.fakemianshi.repository;

import com.fakemianshi.entity.WeaknessTag;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * 薄弱知识点标签 Repository。
 */
public interface WeaknessTagRepository extends JpaRepository<WeaknessTag, Long> {

    /** 查询某项目下的全部弱点标签 */
    List<WeaknessTag> findByProjectId(Long projectId);
}
