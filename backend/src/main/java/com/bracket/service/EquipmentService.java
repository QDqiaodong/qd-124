package com.bracket.service;

import com.bracket.dto.EquipmentRuleRequest;
import com.bracket.dto.PageResult;
import com.bracket.entity.Bracket;
import com.bracket.entity.Equipment;
import com.bracket.entity.MoldBatchRecord;
import com.bracket.repository.BracketRepository;
import com.bracket.repository.EquipmentRepository;
import com.bracket.vo.BracketVO;
import com.bracket.vo.EquipmentVO;
import com.bracket.vo.MoldBatchGateVO;
import com.bracket.vo.RuleChangeDiagnosisVO;
import com.bracket.vo.RuleImpactItemVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class EquipmentService {

    private final EquipmentRepository equipmentRepository;
    private final BracketRepository bracketRepository;
    private final BracketService bracketService;
    private final RuleMatcher ruleMatcher;
    private final MoldBatchService moldBatchService;

    @Autowired
    public EquipmentService(EquipmentRepository equipmentRepository, BracketRepository bracketRepository,
                            BracketService bracketService, RuleMatcher ruleMatcher,
                            MoldBatchService moldBatchService) {
        this.equipmentRepository = equipmentRepository;
        this.bracketRepository = bracketRepository;
        this.bracketService = bracketService;
        this.ruleMatcher = ruleMatcher;
        this.moldBatchService = moldBatchService;
    }

    public PageResult<EquipmentVO> findAll(String code, String name, boolean onlyExceeded, Pageable pageable) {
        // 排序已内置于原生 SQL（create_time DESC, id DESC），这里去掉 Pageable 上的 Sort，
        // 避免 Spring Data 把属性排序直接拼到原生 SQL 后面造成列名歧义
        Pageable unsorted = org.springframework.data.domain.PageRequest.of(
                pageable.getPageNumber(), pageable.getPageSize());
        String codeParam = normalizeParam(code);
        String nameParam = normalizeParam(name);
        Page<Equipment> page = equipmentRepository.findPage(codeParam, nameParam, onlyExceeded, unsorted);
        List<EquipmentVO> voList = convertToVOList(page.getContent());
        return new PageResult<>(voList, page.getTotalElements(), page.getNumber() + 1, page.getSize());
    }

    /** 空白搜索词归一化为 null，交给 SQL 的 :param IS NULL 分支跳过该条件。 */
    private String normalizeParam(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
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
        applyRule(equipment, request);
        Equipment saved = equipmentRepository.save(equipment);
        return convertToVO(saved, currentCountMap());
    }

    /**
     * 规则变更影响诊断：在不写库的前提下，用候选规则校验该设备当前已绑定的支架，
     * 区分「存量绑定不再合规，需要人工处理」与「存量仍合规，仅影响后续绑定」。
     * 诊断使用与绑定校验同一个 {@link RuleMatcher}，保存后结论与本预览一致。
     */
    @Transactional(readOnly = true)
    public RuleChangeDiagnosisVO diagnoseRule(Long id, EquipmentRuleRequest request) {
        Equipment equipment = equipmentRepository.findById(id).orElse(null);
        if (equipment == null) {
            return null;
        }
        validateRule(request);

        boolean changed = !sameRule(equipment, request);
        Equipment candidate = buildCandidate(equipment, request);
        // 复制为可变列表后按支架 ID 稳定排序：容量超额时，最后绑定的支架确定性地落入需人工处理范围
        List<Bracket> boundBrackets = new ArrayList<>(bracketRepository.findByEquipmentId(id));
        boundBrackets.sort(Comparator.comparing(Bracket::getId));

        List<RuleImpactItemVO> existingViolations = new ArrayList<>();
        List<RuleImpactItemVO> compliantItems = new ArrayList<>();
        for (Bracket bracket : boundBrackets) {
            List<String> reasons = ruleMatcher.violations(candidate, bracket);
            RuleImpactItemVO item = toImpactItem(bracket);
            if (!reasons.isEmpty()) {
                item.setImpactType("existing_violation");
                item.setReasons(reasons);
                existingViolations.add(item);
            } else {
                compliantItems.add(item);
            }
        }

        // 容量收紧：型号/尺寸仍合规但超出新上限的已绑定支架需要人工处理
        List<RuleImpactItemVO> capacityImpacts = new ArrayList<>();
        List<RuleImpactItemVO> futureOnlyItems = new ArrayList<>();
        Integer maxBrackets = request.getMaxBrackets();
        if (maxBrackets != null) {
            for (int i = 0; i < compliantItems.size(); i++) {
                RuleImpactItemVO item = compliantItems.get(i);
                if (i >= maxBrackets) {
                    item.setImpactType("capacity_only");
                    item.setReasons(List.of("超出设备最大支架数量（上限" + maxBrackets
                            + "个，当前已绑定" + boundBrackets.size() + "个），需人工解绑或调整容量"));
                    capacityImpacts.add(item);
                } else {
                    item.setImpactType("future_only");
                    item.setReasons(List.of());
                    futureOnlyItems.add(item);
                }
            }
        } else {
            for (RuleImpactItemVO item : compliantItems) {
                item.setImpactType("future_only");
                item.setReasons(List.of());
                futureOnlyItems.add(item);
            }
        }

        int manualCount = existingViolations.size() + capacityImpacts.size();
        RuleChangeDiagnosisVO result = new RuleChangeDiagnosisVO();
        result.setEquipmentId(equipment.getId());
        result.setEquipmentCode(equipment.getEquipmentCode());
        result.setEquipmentName(equipment.getEquipmentName());
        result.setCurrentCount(boundBrackets.size());
        result.setMaxBrackets(maxBrackets);
        result.setRuleChanged(changed);
        result.setExistingViolations(existingViolations);
        result.setCapacityImpacts(capacityImpacts);
        result.setFutureOnlyItems(futureOnlyItems);
        result.setCapacityExceededCount(capacityImpacts.size());
        result.setManualCount(manualCount);
        // 无任何影响：规则未变化，或变化后没有需要人工处理的存量绑定
        result.setNoImpact(!changed && manualCount == 0);
        return result;
    }

    private void applyRule(Equipment equipment, EquipmentRuleRequest request) {
        equipment.setMaxBrackets(request.getMaxBrackets());
        equipment.setAllowedModels(normalizeModels(request.getAllowedModels()));
        equipment.setAllowedMoldModels(MoldBatchService.normalizeMoldModels(request.getAllowedMoldModels()));
        equipment.setMinLength(toBigDecimal(request.getMinLength()));
        equipment.setMaxLength(toBigDecimal(request.getMaxLength()));
        equipment.setMinWidth(toBigDecimal(request.getMinWidth()));
        equipment.setMaxWidth(toBigDecimal(request.getMaxWidth()));
    }

    /** 仅用于诊断比对的临时设备副本，复制规则字段，不参与持久化。 */
    private Equipment buildCandidate(Equipment equipment, EquipmentRuleRequest request) {
        Equipment candidate = new Equipment();
        candidate.setId(equipment.getId());
        candidate.setEquipmentCode(equipment.getEquipmentCode());
        candidate.setEquipmentName(equipment.getEquipmentName());
        applyRule(candidate, request);
        return candidate;
    }

    private boolean sameRule(Equipment equipment, EquipmentRuleRequest request) {
        return java.util.Objects.equals(equipment.getMaxBrackets(), request.getMaxBrackets())
                && java.util.Objects.equals(equipment.getAllowedModels(), normalizeModels(request.getAllowedModels()))
                && java.util.Objects.equals(equipment.getAllowedMoldModels(),
                        MoldBatchService.normalizeMoldModels(request.getAllowedMoldModels()))
                && sameDecimal(equipment.getMinLength(), request.getMinLength())
                && sameDecimal(equipment.getMaxLength(), request.getMaxLength())
                && sameDecimal(equipment.getMinWidth(), request.getMinWidth())
                && sameDecimal(equipment.getMaxWidth(), request.getMaxWidth());
    }

    private boolean sameDecimal(BigDecimal stored, Double requested) {
        if (stored == null || requested == null) {
            return stored == null && requested == null;
        }
        return stored.compareTo(BigDecimal.valueOf(requested)) == 0;
    }

    private RuleImpactItemVO toImpactItem(Bracket bracket) {
        return new RuleImpactItemVO(
                bracket.getId(),
                bracket.getName(),
                bracket.getModel(),
                bracket.getLengthMm() != null ? bracket.getLengthMm().doubleValue() : null,
                bracket.getWidthMm() != null ? bracket.getWidthMm().doubleValue() : null,
                null,
                List.of()
        );
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
        vo.setAllowedMoldModels(MoldBatchService.parseMoldModels(equipment.getAllowedMoldModels()));
        vo.setMinLength(toDouble(equipment.getMinLength()));
        vo.setMaxLength(toDouble(equipment.getMaxLength()));
        vo.setMinWidth(toDouble(equipment.getMinWidth()));
        vo.setMaxWidth(toDouble(equipment.getMaxWidth()));
        // 当前模具批次：仅最新一条换模记录可作为批量挂接/换线放行依据
        MoldBatchRecord currentBatch = moldBatchService.findCurrentRecord(equipment.getId());
        if (currentBatch != null) {
            vo.setCurrentBatchId(currentBatch.getId());
            vo.setCurrentBatchNo(currentBatch.getBatchNo());
            vo.setCurrentMoldModel(currentBatch.getMoldModel());
            vo.setCurrentBatchChangeTime(currentBatch.getChangeTime());
        }
        MoldBatchGateVO gate = moldBatchService.evaluateGate(equipment);
        vo.setMoldBatchReady(Boolean.TRUE.equals(gate.getPassed()));
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
