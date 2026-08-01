package com.fakemianshi.repository;

import com.fakemianshi.entity.Resume;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * 简历 Repository。
 */
public interface ResumeRepository extends JpaRepository<Resume, Long> {

    /** 查询某项目下的全部简历 */
    List<Resume> findByProjectId(Long projectId);
}
