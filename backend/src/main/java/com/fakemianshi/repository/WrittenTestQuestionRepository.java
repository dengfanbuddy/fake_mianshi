package com.fakemianshi.repository;

import com.fakemianshi.entity.WrittenTestQuestion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * 笔试题 Repository。
 */
public interface WrittenTestQuestionRepository extends JpaRepository<WrittenTestQuestion, Long> {

    /** 查询某会话下的题目，按题序排序 */
    List<WrittenTestQuestion> findBySessionIdOrderByOrderNum(Long sessionId);
}
