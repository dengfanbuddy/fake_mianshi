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

    /** 按题目查作答（错题本重练用；一道题在一次交卷里只有一条作答） */
    @Select("SELECT * FROM written_test_answer WHERE question_id = #{questionId} LIMIT 1")
    WrittenTestAnswer findByQuestionId(Long questionId);

    /**
     * 查某项目下全部笔试错题（is_correct = 0）。
     *
     * <p>直接在库里 JOIN 筛：错题本要跨该项目所有笔试场次，先按项目取会话再逐个查作答会变成 N+1。
     * 简答题的 is_correct 可能为 NULL（未自动判分），NULL 不算错题，所以只取 0。
     */
    @Select("""
            SELECT a.* FROM written_test_answer a
            JOIN interview_session s ON s.id = a.session_id
            WHERE s.project_id = #{projectId} AND s.type = 'WRITTEN' AND a.is_correct = 0
            ORDER BY a.session_id DESC, a.id
            """)
    List<WrittenTestAnswer> findWrongByProjectId(Long projectId);
}
