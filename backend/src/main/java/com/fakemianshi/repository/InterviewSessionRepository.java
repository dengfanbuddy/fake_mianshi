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

    /** 查询某项目下指定类型且已完成状态的会话，按完成时间倒序（取第一条即最近一次） */
    List<InterviewSession> findByProjectIdAndTypeAndStatusOrderByCompletedAtDesc(
            Long projectId, String type, String status);
}
