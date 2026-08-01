package com.fakemianshi.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import com.fakemianshi.entity.WrittenTestAnswer;

import java.util.List;

/**
 * 笔试作答 Repository。
 */
@Mapper
public interface WrittenTestAnswerRepository extends BaseMapper<WrittenTestAnswer> {

    /** 查询某会话下的全部作答 */
    @Select("SELECT * FROM written_test_answer WHERE session_id = #{sessionId}")
    List<WrittenTestAnswer> findBySessionId(Long sessionId);
}
