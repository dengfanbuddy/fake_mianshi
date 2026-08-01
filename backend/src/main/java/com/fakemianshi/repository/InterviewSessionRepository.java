package com.fakemianshi.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import com.fakemianshi.entity.InterviewSession;

import java.util.List;

/**
 * 面试会话 Repository。
 */
@Mapper
public interface InterviewSessionRepository extends BaseMapper<InterviewSession> {

    /** 查询某项目下的会话，按创建时间倒序 */
    @Select("SELECT * FROM interview_session WHERE project_id = #{projectId} ORDER BY created_at DESC")
    List<InterviewSession> findByProjectIdOrderByCreatedAtDesc(Long projectId);

    /** 查询某项目下指定类型且已完成状态的会话，按完成时间倒序（取第一条即最近一次） */
    @Select("SELECT * FROM interview_session WHERE project_id = #{projectId} AND type = #{type} AND status = #{status} ORDER BY completed_at DESC")
    List<InterviewSession> findByProjectIdAndTypeAndStatusOrderByCompletedAtDesc(Long projectId, String type, String status);
}
