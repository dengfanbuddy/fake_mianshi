package com.fakemianshi.service.impl;

import com.fakemianshi.config.BusinessException;
import com.fakemianshi.config.ResourceNotFoundException;
import com.fakemianshi.entity.InterviewerPersona;
import com.fakemianshi.repository.InterviewerPersonaRepository;
import com.fakemianshi.service.PersonaService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * 面试官风格服务实现。
 */
@Service
@RequiredArgsConstructor
public class PersonaServiceImpl implements PersonaService {

    private final InterviewerPersonaRepository personaRepository;

    @Override
    @Transactional(readOnly = true)
    public List<InterviewerPersona> findAll() {
        return personaRepository.selectList(null);
    }

    @Override
    @Transactional(readOnly = true)
    public InterviewerPersona findById(Long id) {
        return Optional.ofNullable(personaRepository.selectById(id))
                .orElseThrow(() -> new ResourceNotFoundException("面试官风格不存在: " + id));
    }

    @Override
    @Transactional
    public InterviewerPersona create(InterviewerPersona persona) {
        if (persona.getName() == null || persona.getName().isBlank()) {
            throw new BusinessException("风格名称不能为空");
        }
        persona.setId(null);
        persona.setIsPreset(false);
        personaRepository.insert(persona);
        return persona;
    }

    @Override
    @Transactional
    public InterviewerPersona update(Long id, InterviewerPersona persona) {
        InterviewerPersona existing = findById(id);
        if (Boolean.TRUE.equals(existing.getIsPreset())) {
            throw new BusinessException("预设风格不可修改");
        }
        existing.setName(persona.getName());
        existing.setDescription(persona.getDescription());
        existing.setStyleConfig(persona.getStyleConfig());
        personaRepository.updateById(existing);
        return existing;
    }

    @Override
    @Transactional
    public void delete(Long id) {
        InterviewerPersona existing = findById(id);
        if (Boolean.TRUE.equals(existing.getIsPreset())) {
            throw new BusinessException("预设风格不可删除");
        }
        personaRepository.deleteById(existing.getId());
    }
}
