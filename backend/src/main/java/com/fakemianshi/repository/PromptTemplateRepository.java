package com.fakemianshi.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fakemianshi.entity.PromptTemplate;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Optional;

/**
 * 面试提示词模板 Repository。
 */
@Mapper
public interface PromptTemplateRepository extends BaseMapper<PromptTemplate> {

    /** 查询某职业某场景的模板 */
    @Select("SELECT * FROM prompt_template WHERE occupation = #{occupation} AND scene = #{scene} AND is_active = 1 LIMIT 1")
    Optional<PromptTemplate> findByOccupationAndScene(String occupation, String scene);

    /** 查询某职业的全部场景模板 */
    @Select("SELECT * FROM prompt_template WHERE occupation = #{occupation} AND is_active = 1")
    List<PromptTemplate> findByOccupation(String occupation);

    /** 查询某场景的全部模板（含 default） */
    @Select("SELECT * FROM prompt_template WHERE scene = #{scene} AND is_active = 1")
    List<PromptTemplate> findByScene(String scene);

    /** 全部启用模板 */
    @Select("SELECT * FROM prompt_template WHERE is_active = 1 ORDER BY occupation, scene")
    List<PromptTemplate> findAllActive();

    /** 全部职业列表（含 default） */
    @Select("SELECT DISTINCT occupation FROM prompt_template WHERE is_active = 1 ORDER BY occupation")
    List<String> findOccupations();
}
