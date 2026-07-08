package com.bracket.service;

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

    public EquipmentVO convertToVO(Equipment equipment) {
        if (equipment == null) {
            return null;
        }
        long bracketCount = bracketRepository.findByEquipmentId(equipment.getId()).size();
        return new EquipmentVO(
                equipment.getId(),
                equipment.getEquipmentCode(),
                equipment.getEquipmentName(),
                (int) bracketCount
        );
    }

    public List<EquipmentVO> convertToVOList(List<Equipment> equipments) {
        if (equipments == null || equipments.isEmpty()) {
            return List.of();
        }
        List<Bracket> allBrackets = bracketRepository.findAll();
        Map<Long, Long> bracketCountMap = allBrackets.stream()
                .filter(b -> b.getEquipmentId() != null)
                .collect(Collectors.groupingBy(Bracket::getEquipmentId, Collectors.counting()));
        return equipments.stream()
                .map(e -> new EquipmentVO(
                        e.getId(),
                        e.getEquipmentCode(),
                        e.getEquipmentName(),
                        bracketCountMap.getOrDefault(e.getId(), 0L).intValue()
                ))
                .collect(Collectors.toList());
    }
}
