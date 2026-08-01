package com.fakemianshi.repository;

import com.fakemianshi.entity.WrittenTestAnswer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * 笔试作答 Repository。
 */
public interface WrittenTestAnswerRepository extends JpaRepository<WrittenTestAnswer, Long> {

    /** 查询某会话下的全部作答 */
    List<WrittenTestAnswer> findBySessionId(Long sessionId);
}
