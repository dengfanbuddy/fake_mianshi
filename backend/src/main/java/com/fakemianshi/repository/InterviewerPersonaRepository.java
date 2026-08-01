package com.fakemianshi.repository;

import com.fakemianshi.entity.InterviewerPersona;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * 面试官人设 Repository。
 */
public interface InterviewerPersonaRepository extends JpaRepository<InterviewerPersona, Long> {

    /** 按是否预置筛选 */
    List<InterviewerPersona> findByIsPreset(Boolean isPreset);
}
