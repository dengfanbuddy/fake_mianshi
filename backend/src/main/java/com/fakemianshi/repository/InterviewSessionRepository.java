package com.fakemianshi.repository;

import com.fakemianshi.entity.InterviewSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * 面试会话 Repository。
 */
public interface InterviewSessionRepository extends JpaRepository<InterviewSession, Long> {

    /** 查询某项目下的会话，按创建时间倒序 */
    List<InterviewSession> findByProjectIdOrderByCreatedAtDesc(Long projectId);
}
