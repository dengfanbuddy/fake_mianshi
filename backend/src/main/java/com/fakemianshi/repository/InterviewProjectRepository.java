package com.fakemianshi.repository;

import com.fakemianshi.entity.InterviewProject;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * 面试项目 Repository。
 */
public interface InterviewProjectRepository extends JpaRepository<InterviewProject, Long> {

    /** 按创建时间倒序查询全部项目 */
    List<InterviewProject> findAllByOrderByCreatedAtDesc();
}
