package com.fakemianshi.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import com.fakemianshi.entity.MockInterviewMessage;

import java.util.List;

/**
 * 模拟面试消息 Repository。
 */
@Mapper
public interface MockInterviewMessageRepository extends BaseMapper<MockInterviewMessage> {

    /** 查询某会话下的消息，按创建时间排序 */
    @Select("SELECT * FROM mock_interview_message WHERE session_id = #{sessionId} ORDER BY created_at")
    List<MockInterviewMessage> findBySessionIdOrderByCreatedAt(Long sessionId);
}
