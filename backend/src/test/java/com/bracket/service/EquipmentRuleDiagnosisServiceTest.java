package com.bracket.service;

import com.bracket.dto.EquipmentRuleRequest;
import com.bracket.entity.Bracket;
import com.bracket.entity.Equipment;
import com.bracket.repository.BracketRepository;
import com.bracket.repository.EquipmentRepository;
import com.bracket.vo.RuleChangeDiagnosisVO;
import com.bracket.vo.RuleImpactItemVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 规则变更影响诊断单元测试：
 * 预览不落库、存量不合规/容量超额/仅影响后续绑定的分类、跨字段同时收紧、
 * 无影响场景，以及诊断与绑定校验共用 RuleMatcher 保证原因一致。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EquipmentRuleDiagnosisServiceTest {

    @Mock
    private EquipmentRepository equipmentRepository;

    @Mock
    private BracketRepository bracketRepository;

    @Mock
    private BracketService bracketService;

    @Mock
    private MoldBatchService moldBatchService;

    private EquipmentService equipmentService;

    private Equipment equipment;

    @BeforeEach
    void setUp() {
        equipmentService = new EquipmentService(equipmentRepository, bracketRepository,
                bracketService, new RuleMatcher(), moldBatchService);
        equipment = new Equipment();
        equipment.setId(1L);
        equipment.setEquipmentCode("FK-001");
        equipment.setEquipmentName("1号封口机");
        when(equipmentRepository.findById(1L)).thenReturn(Optional.of(equipment));
    }

    private EquipmentRuleRequest rule(Integer max, String models,
                                      Double minLen, Double maxLen, Double minWid, Double maxWid) {
        EquipmentRuleRequest request = new EquipmentRuleRequest();
        request.setMaxBrackets(max);
        request.setAllowedModels(models);
        request.setMinLength(minLen);
        request.setMaxLength(maxLen);
        request.setMinWidth(minWid);
        request.setMaxWidth(maxWid);
        return request;
    }

    private Bracket bracket(long id, String name, String model, double length, double width) {
        Bracket b = new Bracket();
        b.setId(id);
        b.setName(name);
        b.setModel(model);
        b.setLengthMm(BigDecimal.valueOf(length));
        b.setWidthMm(BigDecimal.valueOf(width));
        b.setEquipmentId(1L);
        return b;
    }

    @Test
    void diagnose_equipmentNotFound_returnsNull() {
        when(equipmentRepository.findById(404L)).thenReturn(Optional.empty());
        assertNull(equipmentService.diagnoseRule(404L, rule(null, null, null, null, null, null)));
    }

    @Test
    void classify_existingViolation_capacityOnly_futureOnly_acrossFields() {
        // 原规则：最大 5 个、允许 A-01，无尺寸限制；已绑定 4 个
        equipment.setMaxBrackets(5);
        equipment.setAllowedModels("A-01");
        Bracket okFirst = bracket(10, "支架甲", "A-01", 200, 100);
        Bracket okSecond = bracket(11, "支架乙", "A-02", 150, 90);
        Bracket okThird = bracket(13, "支架丁", "A-01", 120, 95);
        Bracket wrongModel = bracket(12, "支架丙", "B-X", 500, 100);
        when(bracketRepository.findByEquipmentId(1L))
                .thenReturn(new java.util.ArrayList<>(List.of(okFirst, okSecond, wrongModel, okThird)));

        // 新规则跨字段同时收紧：容量 2、型号 A-01/A-02、长度 100~300
        RuleChangeDiagnosisVO result = equipmentService.diagnoseRule(
                1L, rule(2, "A-01,A-02", 100.0, 300.0, null, null));

        assertEquals(4, result.getCurrentCount());
        assertEquals(Integer.valueOf(2), result.getMaxBrackets());
        assertTrue(result.getRuleChanged());
        assertFalse(result.getNoImpact());

        // 支架丙：型号与长度双重冲突，原因全部聚合
        assertEquals(1, result.getExistingViolations().size());
        RuleImpactItemVO violation = result.getExistingViolations().get(0);
        assertEquals(12L, violation.getBracketId());
        assertEquals("existing_violation", violation.getImpactType());
        assertTrue(violation.getReasons().stream().anyMatch(r -> r.contains("型号不在允许范围内")));
        assertTrue(violation.getReasons().stream().anyMatch(r -> r.contains("长度超出允许范围")));

        // 合规支架共 3 个，容量收紧到 2：按 id 排序后最后 1 个（支架丁）标记容量超额
        assertEquals(1, result.getCapacityImpacts().size());
        assertEquals(13L, result.getCapacityImpacts().get(0).getBracketId());
        assertEquals("capacity_only", result.getCapacityImpacts().get(0).getImpactType());
        assertTrue(result.getCapacityImpacts().get(0).getReasons().get(0).contains("超出设备最大支架数量"));
        assertEquals(1, result.getCapacityExceededCount());

        // 前 2 个合规支架仍在容量内：仅影响后续绑定
        assertEquals(2, result.getFutureOnlyItems().size());
        assertEquals(10L, result.getFutureOnlyItems().get(0).getBracketId());
        assertEquals(11L, result.getFutureOnlyItems().get(1).getBracketId());
        assertEquals("future_only", result.getFutureOnlyItems().get(0).getImpactType());

        assertEquals(2, result.getManualCount());
        // 预览不写库
        verify(equipmentRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void capacityOnlyTightening_allCompliant_marksLatestAsManual() {
        // 无型号/尺寸规则，3 个已绑定全部合规；容量从不限收紧到 2
        when(bracketRepository.findByEquipmentId(1L)).thenReturn(new java.util.ArrayList<>(List.of(
                bracket(1, "支架1", "A-01", 100, 50),
                bracket(2, "支架2", "A-01", 100, 50),
                bracket(3, "支架3", "A-01", 100, 50))));

        RuleChangeDiagnosisVO result = equipmentService.diagnoseRule(
                1L, rule(2, null, null, null, null, null));

        assertEquals(0, result.getExistingViolations().size());
        assertEquals(1, result.getCapacityImpacts().size());
        assertEquals(3L, result.getCapacityImpacts().get(0).getBracketId());
        assertEquals(2, result.getFutureOnlyItems().size());
        assertEquals(1, result.getManualCount());
        assertFalse(result.getNoImpact());
    }

    @Test
    void diagnoseWidthRangeTightening_bothSidesViolationsAggregated() {
        equipment.setMaxBrackets(5);
        when(bracketRepository.findByEquipmentId(1L)).thenReturn(List.of(
                bracket(1, "窄支架", "A-01", 200, 50),
                bracket(2, "宽支架", "A-01", 200, 300)));

        // 仅收紧宽度范围：下限与上限两侧各命中一个
        RuleChangeDiagnosisVO result = equipmentService.diagnoseRule(
                1L, rule(5, "A-01", null, null, 80.0, 200.0));

        assertEquals(2, result.getExistingViolations().size());
        RuleImpactItemVO narrow = result.getExistingViolations().stream()
                .filter(i -> i.getBracketId() == 1L).findFirst().orElseThrow();
        RuleImpactItemVO wide = result.getExistingViolations().stream()
                .filter(i -> i.getBracketId() == 2L).findFirst().orElseThrow();
        assertTrue(narrow.getReasons().get(0).contains("宽度低于允许范围"));
        assertTrue(wide.getReasons().get(0).contains("宽度超出允许范围"));
        assertEquals(0, result.getCapacityImpacts().size());
        assertEquals(0, result.getFutureOnlyItems().size());
        assertEquals(2, result.getManualCount());
        assertTrue(result.getRuleChanged());
    }

    @Test
    void looseningRule_allCompliant_noManualHandlingAndFutureBindingsOnly() {
        // 原规则较紧：容量 1、仅 A-01、长度上限 200；绑定 2 个（存量本身已超额）
        equipment.setMaxBrackets(1);
        equipment.setAllowedModels("A-01");
        equipment.setMaxLength(BigDecimal.valueOf(200));
        when(bracketRepository.findByEquipmentId(1L)).thenReturn(List.of(
                bracket(1, "支架1", "A-01", 180, 90),
                bracket(2, "支架2", "A-02", 260, 90)));

        // 放宽：取消容量/型号/长度限制
        RuleChangeDiagnosisVO result = equipmentService.diagnoseRule(
                1L, rule(null, null, null, null, null, null));

        assertEquals(0, result.getManualCount());
        assertEquals(0, result.getExistingViolations().size());
        assertEquals(0, result.getCapacityImpacts().size());
        assertEquals(2, result.getFutureOnlyItems().size());
        assertFalse(result.getNoImpact(), "规则发生了变化，不属于「无影响」");
        assertTrue(result.getRuleChanged());
    }

    @Test
    void unchangedRuleAndEverythingCompliant_isNoImpact() {
        equipment.setMaxBrackets(5);
        equipment.setAllowedModels("A-01");
        equipment.setMinLength(BigDecimal.valueOf(100));
        equipment.setMaxLength(BigDecimal.valueOf(300));
        when(bracketRepository.findByEquipmentId(1L)).thenReturn(List.of(
                bracket(1, "支架1", "A-01", 200, 100)));

        RuleChangeDiagnosisVO result = equipmentService.diagnoseRule(
                1L, rule(5, " A-01,", 100.0, 300.0, null, null));

        assertFalse(result.getRuleChanged(), "型号串空白与逗号归一化后应视为未变化");
        assertTrue(result.getNoImpact());
        assertEquals(0, result.getManualCount());
        assertEquals(1, result.getFutureOnlyItems().size());
    }

    @Test
    void noBoundBrackets_changeOnlyAffectsFutureBindings() {
        when(bracketRepository.findByEquipmentId(1L)).thenReturn(List.of());
        RuleChangeDiagnosisVO result = equipmentService.diagnoseRule(
                1L, rule(1, "A-01", null, null, null, null));

        assertEquals(0, result.getCurrentCount());
        assertEquals(0, result.getManualCount());
        assertTrue(result.getExistingViolations().isEmpty());
        assertTrue(result.getCapacityImpacts().isEmpty());
        assertTrue(result.getFutureOnlyItems().isEmpty());
        assertFalse(result.getNoImpact(), "从无规则变为有规则，后续绑定受限");
    }

    @Test
    void diagnose_invalidRange_throwsAndDoesNotPersist() {
        assertThrows(IllegalArgumentException.class, () ->
                equipmentService.diagnoseRule(1L, rule(null, null, 300.0, 100.0, null, null)));
        verify(equipmentRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void diagnose_negativeMaxBrackets_throws() {
        assertThrows(IllegalArgumentException.class, () ->
                equipmentService.diagnoseRule(1L, rule(-1, null, null, null, null, null)));
    }

    @Test
    void sameReasonAsBindValidation_whenModelForbiddenAfterRuleChange() {
        // 诊断出的型号冲突文案，与 RuleMatcher（绑定校验复用组件）对同一支架的判定一致
        equipment.setAllowedModels(null);
        Bracket b = bracket(7, "支架7", "OLD-MODEL", 100, 50);
        when(bracketRepository.findByEquipmentId(1L)).thenReturn(List.of(b));

        RuleChangeDiagnosisVO result = equipmentService.diagnoseRule(
                1L, rule(null, "NEW-MODEL", null, null, null, null));

        String diagnoseReason = result.getExistingViolations().get(0).getReasons().get(0);
        Equipment candidate = new Equipment();
        candidate.setAllowedModels("NEW-MODEL");
        String bindReason = new RuleMatcher().matchRule(candidate, b);
        assertEquals(bindReason, diagnoseReason);
    }
}
