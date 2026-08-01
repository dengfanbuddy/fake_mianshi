package com.fakemianshi.repository;

import com.fakemianshi.entity.MockInterviewMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * 模拟面试消息 Repository。
 */
public interface MockInterviewMessageRepository extends JpaRepository<MockInterviewMessage, Long> {

    /** 查询某会话下的消息，按创建时间排序 */
    List<MockInterviewMessage> findBySessionIdOrderByCreatedAt(Long sessionId);
}
