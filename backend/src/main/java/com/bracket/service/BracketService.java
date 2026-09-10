package com.bracket.service;

import com.bracket.dto.PageResult;
import com.bracket.entity.Bracket;
import com.bracket.entity.Equipment;
import com.bracket.repository.BracketRepository;
import com.bracket.repository.EquipmentRepository;
import com.bracket.vo.BracketVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class BracketService {

    private final BracketRepository bracketRepository;
    private final EquipmentRepository equipmentRepository;
    private final RedisTemplate<String, Object> redisTemplate;

    private static final String POPULAR_MODELS_KEY = "bracket:models:popular";

    @Autowired
    public BracketService(BracketRepository bracketRepository, EquipmentRepository equipmentRepository, RedisTemplate<String, Object> redisTemplate) {
        this.bracketRepository = bracketRepository;
        this.equipmentRepository = equipmentRepository;
        this.redisTemplate = redisTemplate;
    }

    public PageResult<BracketVO> findAll(String name, String model, Integer bindStatus, Pageable pageable) {
        Page<Bracket> page;
        boolean hasName = name != null && !name.trim().isEmpty();
        boolean hasModel = model != null && !model.trim().isEmpty();
        boolean onlyUnbound = bindStatus != null && bindStatus == 0;
        boolean onlyBound = bindStatus != null && bindStatus == 1;
        if (onlyUnbound) {
            if (hasName && hasModel) {
                page = bracketRepository.findByNameContainingAndModelContainingAndEquipmentIdIsNull(name, model, pageable);
            } else if (hasName) {
                page = bracketRepository.findByNameContainingAndEquipmentIdIsNull(name, pageable);
            } else if (hasModel) {
                page = bracketRepository.findByModelContainingAndEquipmentIdIsNull(model, pageable);
            } else {
                page = bracketRepository.findByEquipmentIdIsNull(pageable);
            }
        } else if (onlyBound) {
            if (hasName && hasModel) {
                page = bracketRepository.findByNameContainingAndModelContainingAndEquipmentIdIsNotNull(name, model, pageable);
            } else if (hasName) {
                page = bracketRepository.findByNameContainingAndEquipmentIdIsNotNull(name, pageable);
            } else if (hasModel) {
                page = bracketRepository.findByModelContainingAndEquipmentIdIsNotNull(model, pageable);
            } else {
                page = bracketRepository.findByEquipmentIdIsNotNull(pageable);
            }
        } else if (!hasName && !hasModel) {
            page = bracketRepository.findAll(pageable);
        } else if (hasName && !hasModel) {
            page = bracketRepository.findByNameContaining(name, pageable);
        } else if (!hasName) {
            page = bracketRepository.findByModelContaining(model, pageable);
        } else {
            page = bracketRepository.findByNameContainingAndModelContaining(name, model, pageable);
        }
        List<BracketVO> voList = convertToVOList(page.getContent());
        return new PageResult<>(voList, page.getTotalElements(), page.getNumber() + 1, page.getSize());
    }

    public BracketVO findById(Long id) {
        Bracket bracket = bracketRepository.findById(id).orElse(null);
        if (bracket == null) {
            return null;
        }
        return convertToVO(bracket);
    }

    @Transactional
    public BracketVO save(com.bracket.dto.BracketCreateRequest request) {
        Bracket bracket = new Bracket();
        bracket.setName(request.getName());
        bracket.setModel(request.getModel());
        bracket.setLengthMm(request.getLengthMm());
        bracket.setWidthMm(request.getWidthMm());
        Bracket saved = bracketRepository.save(bracket);
        return convertToVO(saved);
    }

    @Transactional
    public BracketVO update(Long id, com.bracket.dto.BracketCreateRequest request) {
        Bracket existing = bracketRepository.findById(id).orElse(null);
        if (existing == null) {
            return null;
        }
        existing.setName(request.getName());
        existing.setModel(request.getModel());
        existing.setLengthMm(request.getLengthMm());
        existing.setWidthMm(request.getWidthMm());
        Bracket updated = bracketRepository.save(existing);
        return convertToVO(updated);
    }

    @Transactional
    public void delete(Long id) {
        Bracket bracket = bracketRepository.findById(id).orElse(null);
        if (bracket != null) {
            bracket.setEquipmentId(null);
            bracketRepository.save(bracket);
        }
        bracketRepository.deleteById(id);
    }

    @SuppressWarnings("unchecked")
    public List<String> getPopularModels() {
        List<Object> cached = redisTemplate.opsForList().range(POPULAR_MODELS_KEY, 0, -1);
        if (cached != null && !cached.isEmpty()) {
            return cached.stream().map(Object::toString).collect(Collectors.toList());
        }
        List<Bracket> brackets = bracketRepository.findAll();
        List<String> models = brackets.stream()
                .map(Bracket::getModel)
                .distinct()
                .collect(Collectors.toList());
        if (!models.isEmpty()) {
            redisTemplate.opsForList().rightPushAll(POPULAR_MODELS_KEY, models.toArray());
        }
        return models;
    }

    public Map<String, Long> getStats() {
        Map<String, Long> stats = new HashMap<>();
        long total = bracketRepository.count();
        long bound = bracketRepository.countByEquipmentIdIsNotNull();
        long unbound = bracketRepository.countByEquipmentIdIsNull();
        stats.put("total", total);
        stats.put("bound", bound);
        stats.put("unbound", unbound);
        return stats;
    }

    public BracketVO convertToVO(Bracket bracket) {
        if (bracket == null) {
            return null;
        }
        String equipmentName = null;
        if (bracket.getEquipmentId() != null) {
            Equipment equipment = equipmentRepository.findById(bracket.getEquipmentId()).orElse(null);
            if (equipment != null) {
                equipmentName = equipment.getEquipmentName();
            }
        }
        return new BracketVO(
                bracket.getId(),
                bracket.getName(),
                bracket.getModel(),
                bracket.getLengthMm() != null ? bracket.getLengthMm().doubleValue() : null,
                bracket.getWidthMm() != null ? bracket.getWidthMm().doubleValue() : null,
                bracket.getEquipmentId(),
                equipmentName,
                bracket.getCreateTime(),
                bracket.getUpdateTime()
        );
    }

    public List<BracketVO> convertToVOList(List<Bracket> brackets) {
        if (brackets == null || brackets.isEmpty()) {
            return List.of();
        }
        List<Long> equipmentIds = brackets.stream()
                .map(Bracket::getEquipmentId)
                .filter(id -> id != null)
                .distinct()
                .collect(Collectors.toList());
        Map<Long, String> equipmentNameMap = new HashMap<>();
        if (!equipmentIds.isEmpty()) {
            List<Equipment> equipments = equipmentRepository.findAllById(equipmentIds);
            for (Equipment e : equipments) {
                equipmentNameMap.put(e.getId(), e.getEquipmentName());
            }
        }
        return brackets.stream()
                .map(b -> {
                    BracketVO vo = new BracketVO();
                    vo.setId(b.getId());
                    vo.setName(b.getName());
                    vo.setModel(b.getModel());
                    vo.setLength(b.getLengthMm() != null ? b.getLengthMm().doubleValue() : null);
                    vo.setWidth(b.getWidthMm() != null ? b.getWidthMm().doubleValue() : null);
                    vo.setEquipmentId(b.getEquipmentId());
                    vo.setEquipmentName(b.getEquipmentId() != null ? equipmentNameMap.get(b.getEquipmentId()) : null);
                    vo.setCreateTime(b.getCreateTime());
                    vo.setUpdateTime(b.getUpdateTime());
                    return vo;
                })
                .collect(Collectors.toList());
    }
}
