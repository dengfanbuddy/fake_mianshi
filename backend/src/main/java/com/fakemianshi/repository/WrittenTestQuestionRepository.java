package com.fakemianshi.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import com.fakemianshi.entity.WrittenTestQuestion;

import java.util.List;

/**
 * 笔试题 Repository。
 */
@Mapper
public interface WrittenTestQuestionRepository extends BaseMapper<WrittenTestQuestion> {

    /** 查询某会话下的题目，按题序排序 */
    @Select("SELECT * FROM written_test_question WHERE session_id = #{sessionId} ORDER BY order_num")
    List<WrittenTestQuestion> findBySessionIdOrderByOrderNum(Long sessionId);
}
