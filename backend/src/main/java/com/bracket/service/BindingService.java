package com.bracket.service;

import com.bracket.entity.Bracket;
import com.bracket.entity.Equipment;
import com.bracket.repository.BracketRepository;
import com.bracket.repository.EquipmentRepository;
import com.bracket.vo.BindCheckItemVO;
import com.bracket.vo.BindCheckResultVO;
import com.bracket.vo.BindConfirmResultVO;
import com.bracket.vo.BracketVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class BindingService {

    private final BracketRepository bracketRepository;
    private final EquipmentRepository equipmentRepository;
    private final BracketService bracketService;

    @Autowired
    public BindingService(BracketRepository bracketRepository, EquipmentRepository equipmentRepository,
                          BracketService bracketService) {
        this.bracketRepository = bracketRepository;
        this.equipmentRepository = equipmentRepository;
        this.bracketService = bracketService;
    }

    @Transactional
    public BracketVO unbind(Long bracketId) {
        Bracket bracket = bracketRepository.findById(bracketId).orElse(null);
        if (bracket == null) {
            return null;
        }
        bracket.setEquipmentId(null);
        Bracket saved = bracketRepository.save(bracket);
        return bracketService.convertToVO(saved);
    }

    /**
     * 单个绑定前的配套规则校验。
     */
    public BindCheckResultVO checkBind(Long bracketId, Long equipmentId) {
        return doCheck(List.of(bracketId), equipmentId);
    }

    /**
     * 批量绑定前的配套规则校验。
     */
    public BindCheckResultVO checkBatchBind(List<Long> bracketIds, Long equipmentId) {
        return doCheck(deduplicate(bracketIds), equipmentId);
    }

    /**
     * 单个绑定确认：确认前重新校验，仅绑定通过项。
     */
    @Transactional
    public BindConfirmResultVO confirmBind(Long bracketId, Long equipmentId) {
        BindCheckResultVO check = doCheck(List.of(bracketId), equipmentId);
        return doConfirm(check, equipmentId);
    }

    /**
     * 批量绑定确认：确认前重新校验，仅绑定通过项。
     */
    @Transactional
    public BindConfirmResultVO confirmBatchBind(List<Long> bracketIds, Long equipmentId) {
        BindCheckResultVO check = doCheck(deduplicate(bracketIds), equipmentId);
        return doConfirm(check, equipmentId);
    }

    private BindCheckResultVO doCheck(List<Long> bracketIds, Long equipmentId) {
        Equipment equipment = equipmentId == null ? null
                : equipmentRepository.findById(equipmentId).orElse(null);
        BindCheckResultVO result = new BindCheckResultVO();
        if (equipmentId != null) {
            result.setEquipmentId(equipmentId);
        }
        if (equipment == null) {
            result.setCurrentCount(0);
            result.setItems(bracketIds.stream()
                    .map(id -> new BindCheckItemVO(id, null, null, null, null, false, "目标设备不存在"))
                    .collect(Collectors.toList()));
            result.setPassedItems(List.of());
            result.setConflicts(new ArrayList<>(result.getItems()));
            return result;
        }
        result.setEquipmentCode(equipment.getEquipmentCode());
        result.setEquipmentName(equipment.getEquipmentName());
        result.setMaxBrackets(equipment.getMaxBrackets());

        List<Bracket> boundBrackets = bracketRepository.findByEquipmentId(equipmentId);
        int currentCount = boundBrackets.size();
        result.setCurrentCount(currentCount);

        Map<Long, Bracket> bracketMap = bracketRepository.findAllById(bracketIds).stream()
                .collect(Collectors.toMap(Bracket::getId, Function.identity()));

        // 容量：已在该设备上的支架重新绑定不额外占用；空表示不限容量
        Set<Long> alreadyOnEquipment = boundBrackets.stream()
                .map(Bracket::getId)
                .collect(Collectors.toSet());
        Integer remainingSlots = equipment.getMaxBrackets() == null ? null
                : Math.max(0, equipment.getMaxBrackets() - currentCount);
        result.setAvailableSlots(remainingSlots);

        List<BindCheckItemVO> items = new ArrayList<>();
        for (Long bracketId : bracketIds) {
            Bracket bracket = bracketMap.get(bracketId);
            if (bracket == null) {
                items.add(new BindCheckItemVO(bracketId, null, null, null, null, false, "支架不存在"));
                continue;
            }
            String reason = matchRule(equipment, bracket);
            if (reason == null && !alreadyOnEquipment.contains(bracketId)) {
                if (remainingSlots != null && remainingSlots <= 0) {
                    reason = "超出设备最大支架数量（上限" + equipment.getMaxBrackets() + "个，当前已占用" + currentCount + "个）";
                } else if (remainingSlots != null) {
                    remainingSlots--;
                }
            }
            items.add(new BindCheckItemVO(
                    bracket.getId(),
                    bracket.getName(),
                    bracket.getModel(),
                    bracket.getLengthMm() != null ? bracket.getLengthMm().doubleValue() : null,
                    bracket.getWidthMm() != null ? bracket.getWidthMm().doubleValue() : null,
                    reason == null,
                    reason
            ));
        }
        result.setItems(items);
        result.setPassedItems(items.stream().filter(BindCheckItemVO::getPassed).collect(Collectors.toList()));
        result.setConflicts(items.stream().filter(i -> !i.getPassed()).collect(Collectors.toList()));
        return result;
    }

    private BindConfirmResultVO doConfirm(BindCheckResultVO check, Long equipmentId) {
        List<Long> passedIds = check.getPassedItems().stream()
                .map(BindCheckItemVO::getBracketId)
                .collect(Collectors.toList());
        if (passedIds.isEmpty()) {
            return new BindConfirmResultVO(0, List.of());
        }
        List<Bracket> brackets = bracketRepository.findAllById(passedIds);
        for (Bracket bracket : brackets) {
            bracket.setEquipmentId(equipmentId);
        }
        List<Bracket> saved = bracketRepository.saveAll(brackets);
        List<BracketVO> voList = bracketService.convertToVOList(saved);
        return new BindConfirmResultVO(voList.size(), voList);
    }

    /**
     * 按设备配套规则校验单个支架，返回冲突原因；通过时返回 null。
     * 规则字段为空表示该项不限制；设备未配置任何规则时全部放行。
     */
    private String matchRule(Equipment equipment, Bracket bracket) {
        List<String> allowedModels = EquipmentService.parseModels(equipment.getAllowedModels());
        if (!allowedModels.isEmpty() && !allowedModels.contains(bracket.getModel())) {
            return "型号不在允许范围内（允许：" + String.join("、", allowedModels) + "）";
        }
        String lengthReason = checkDimension("长度", bracket.getLengthMm(),
                equipment.getMinLength(), equipment.getMaxLength());
        if (lengthReason != null) {
            return lengthReason;
        }
        String widthReason = checkDimension("宽度", bracket.getWidthMm(),
                equipment.getMinWidth(), equipment.getMaxWidth());
        if (widthReason != null) {
            return widthReason;
        }
        return null;
    }

    private String checkDimension(String label, BigDecimal value, BigDecimal min, BigDecimal max) {
        if (min == null && max == null) {
            return null;
        }
        if (value == null) {
            return label + "尺寸缺失，无法满足" + formatRange(label, min, max);
        }
        if (min != null && value.compareTo(min) < 0) {
            return label + "低于允许范围（" + formatRange(label, min, max) + "）";
        }
        if (max != null && value.compareTo(max) > 0) {
            return label + "超出允许范围（" + formatRange(label, min, max) + "）";
        }
        return null;
    }

    private String formatRange(String label, BigDecimal min, BigDecimal max) {
        String minText = min != null ? min.stripTrailingZeros().toPlainString() : "不限";
        String maxText = max != null ? max.stripTrailingZeros().toPlainString() : "不限";
        return label + minText + "~" + maxText + "mm";
    }

    private List<Long> deduplicate(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return new ArrayList<>(new LinkedHashSet<>(ids));
    }
}
