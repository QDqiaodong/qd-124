package com.bracket.service;

import com.bracket.dto.EquipmentRuleRequest;
import com.bracket.dto.PageResult;
import com.bracket.entity.Bracket;
import com.bracket.entity.Equipment;
import com.bracket.repository.BracketRepository;
import com.bracket.repository.EquipmentRepository;
import com.bracket.vo.BracketVO;
import com.bracket.vo.EquipmentVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class EquipmentService {

    private final EquipmentRepository equipmentRepository;
    private final BracketRepository bracketRepository;
    private final BracketService bracketService;

    @Autowired
    public EquipmentService(EquipmentRepository equipmentRepository, BracketRepository bracketRepository, BracketService bracketService) {
        this.equipmentRepository = equipmentRepository;
        this.bracketRepository = bracketRepository;
        this.bracketService = bracketService;
    }

    public PageResult<EquipmentVO> findAll(String code, String name, Pageable pageable) {
        Page<Equipment> page;
        boolean hasCode = code != null && !code.trim().isEmpty();
        boolean hasName = name != null && !name.trim().isEmpty();
        if (!hasCode && !hasName) {
            page = equipmentRepository.findAll(pageable);
        } else if (hasCode && !hasName) {
            page = equipmentRepository.findByEquipmentCodeContaining(code, pageable);
        } else if (!hasCode && hasName) {
            page = equipmentRepository.findByEquipmentNameContaining(name, pageable);
        } else {
            page = equipmentRepository.findByEquipmentCodeContainingAndEquipmentNameContaining(code, name, pageable);
        }
        List<EquipmentVO> voList = convertToVOList(page.getContent());
        return new PageResult<>(voList, page.getTotalElements(), page.getNumber() + 1, page.getSize());
    }

    public List<EquipmentVO> findAllWithBracketCount() {
        List<Equipment> equipments = equipmentRepository.findAll();
        return convertToVOList(equipments);
    }

    public List<BracketVO> findBracketsByEquipmentId(Long equipmentId) {
        List<Bracket> brackets = bracketRepository.findByEquipmentId(equipmentId);
        return bracketService.convertToVOList(brackets);
    }

    public long getUnboundCount() {
        return bracketRepository.countByEquipmentIdIsNull();
    }

    @Transactional
    public EquipmentVO updateRule(Long id, EquipmentRuleRequest request) {
        Equipment equipment = equipmentRepository.findById(id).orElse(null);
        if (equipment == null) {
            return null;
        }
        validateRule(request);
        // 仅更新配套规则，不改动设备信息与已有绑定
        equipment.setMaxBrackets(request.getMaxBrackets());
        equipment.setAllowedModels(normalizeModels(request.getAllowedModels()));
        equipment.setMinLength(toBigDecimal(request.getMinLength()));
        equipment.setMaxLength(toBigDecimal(request.getMaxLength()));
        equipment.setMinWidth(toBigDecimal(request.getMinWidth()));
        equipment.setMaxWidth(toBigDecimal(request.getMaxWidth()));
        Equipment saved = equipmentRepository.save(equipment);
        return convertToVO(saved, currentCountMap());
    }

    private void validateRule(EquipmentRuleRequest request) {
        if (request.getMaxBrackets() != null && request.getMaxBrackets() < 0) {
            throw new IllegalArgumentException("最大支架数量不能为负数");
        }
        validateRange(request.getMinLength(), request.getMaxLength(), "长度");
        validateRange(request.getMinWidth(), request.getMaxWidth(), "宽度");
    }

    private void validateRange(Double min, Double max, String label) {
        if (min != null && min < 0) {
            throw new IllegalArgumentException(label + "下限不能为负数");
        }
        if (max != null && max < 0) {
            throw new IllegalArgumentException(label + "上限不能为负数");
        }
        if (min != null && max != null && min > max) {
            throw new IllegalArgumentException(label + "下限不能大于上限");
        }
    }

    public EquipmentVO convertToVO(Equipment equipment) {
        if (equipment == null) {
            return null;
        }
        return convertToVO(equipment, currentCountMap());
    }

    public EquipmentVO convertToVO(Equipment equipment, Map<Long, Long> countMap) {
        int bracketCount = countMap.getOrDefault(equipment.getId(), 0L).intValue();
        EquipmentVO vo = new EquipmentVO(
                equipment.getId(),
                equipment.getEquipmentCode(),
                equipment.getEquipmentName(),
                bracketCount
        );
        vo.setMaxBrackets(equipment.getMaxBrackets());
        vo.setAllowedModels(parseModels(equipment.getAllowedModels()));
        vo.setMinLength(toDouble(equipment.getMinLength()));
        vo.setMaxLength(toDouble(equipment.getMaxLength()));
        vo.setMinWidth(toDouble(equipment.getMinWidth()));
        vo.setMaxWidth(toDouble(equipment.getMaxWidth()));
        boolean configured = isRuleConfigured(equipment);
        vo.setRuleConfigured(configured);
        vo.setCapacityStatus(resolveCapacityStatus(configured, equipment.getMaxBrackets(), bracketCount));
        return vo;
    }

    public List<EquipmentVO> convertToVOList(List<Equipment> equipments) {
        if (equipments == null || equipments.isEmpty()) {
            return List.of();
        }
        Map<Long, Long> countMap = currentCountMap();
        return equipments.stream()
                .map(e -> convertToVO(e, countMap))
                .collect(Collectors.toList());
    }

    private Map<Long, Long> currentCountMap() {
        return bracketRepository.findAll().stream()
                .filter(b -> b.getEquipmentId() != null)
                .collect(Collectors.groupingBy(Bracket::getEquipmentId, Collectors.counting()));
    }

    private String resolveCapacityStatus(boolean configured, Integer maxBrackets, int bracketCount) {
        if (!configured || maxBrackets == null) {
            return "unlimited";
        }
        if (bracketCount > maxBrackets) {
            return "exceeded";
        }
        if (bracketCount == maxBrackets) {
            return "full";
        }
        return "normal";
    }

    private boolean isRuleConfigured(Equipment equipment) {
        return equipment.getMaxBrackets() != null
                || (equipment.getAllowedModels() != null && !equipment.getAllowedModels().trim().isEmpty())
                || equipment.getMinLength() != null
                || equipment.getMaxLength() != null
                || equipment.getMinWidth() != null
                || equipment.getMaxWidth() != null;
    }

    /** 规则配置中按逗号分隔的允许型号列表。 */
    public static List<String> parseModels(String allowedModels) {
        if (allowedModels == null || allowedModels.trim().isEmpty()) {
            return Collections.emptyList();
        }
        return Arrays.stream(allowedModels.split("[,，]"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .distinct()
                .collect(Collectors.toList());
    }

    private String normalizeModels(String allowedModels) {
        List<String> models = parseModels(allowedModels);
        return models.isEmpty() ? null : String.join(",", models);
    }

    private BigDecimal toBigDecimal(Double value) {
        return value != null ? BigDecimal.valueOf(value) : null;
    }

    private Double toDouble(BigDecimal value) {
        return value != null ? value.doubleValue() : null;
    }
}
