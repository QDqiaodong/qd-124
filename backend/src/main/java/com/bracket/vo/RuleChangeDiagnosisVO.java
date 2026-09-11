package com.bracket.vo;

import java.util.List;

/**
 * 设备配套规则变更影响诊断结果（不落库）。
 */
public class RuleChangeDiagnosisVO {

    private Long equipmentId;
    private String equipmentCode;
    private String equipmentName;
    /** 当前已绑定支架总数（诊断前） */
    private Integer currentCount;
    /** 新规则最大支架数量，空表示不限制 */
    private Integer maxBrackets;
    /** 提交规则与当前已保存规则是否存在差异 */
    private Boolean ruleChanged;

    /** 存量绑定在新规则下不再合规（型号/尺寸冲突），需要人工处理的支架 */
    private List<RuleImpactItemVO> existingViolations;
    /** 型号/尺寸仍合规，但容量收紧后超出新上限的支架 */
    private List<RuleImpactItemVO> capacityImpacts;
    /** 在新规则下仍合规的已绑定支架，仅后续绑定受限 */
    private List<RuleImpactItemVO> futureOnlyItems;

    /** 需要人工处理的总数（存量不合规 + 容量超额） */
    private Integer manualCount;
    /** 容量超出新上限的数量 */
    private Integer capacityExceededCount;
    /** 是否不存在任何影响 */
    private Boolean noImpact;

    public RuleChangeDiagnosisVO() {
    }

    public Long getEquipmentId() {
        return equipmentId;
    }

    public void setEquipmentId(Long equipmentId) {
        this.equipmentId = equipmentId;
    }

    public String getEquipmentCode() {
        return equipmentCode;
    }

    public void setEquipmentCode(String equipmentCode) {
        this.equipmentCode = equipmentCode;
    }

    public String getEquipmentName() {
        return equipmentName;
    }

    public void setEquipmentName(String equipmentName) {
        this.equipmentName = equipmentName;
    }



    public Integer getCurrentCount() {
        return currentCount;
    }

    public void setCurrentCount(Integer currentCount) {
        this.currentCount = currentCount;
    }

    public Integer getMaxBrackets() {
        return maxBrackets;
    }

    public void setMaxBrackets(Integer maxBrackets) {
        this.maxBrackets = maxBrackets;
    }

    public Boolean getRuleChanged() {
        return ruleChanged;
    }

    public void setRuleChanged(Boolean ruleChanged) {
        this.ruleChanged = ruleChanged;
    }

    public List<RuleImpactItemVO> getExistingViolations() {
        return existingViolations;
    }

    public void setExistingViolations(List<RuleImpactItemVO> existingViolations) {
        this.existingViolations = existingViolations;
    }

    public List<RuleImpactItemVO> getCapacityImpacts() {
        return capacityImpacts;
    }

    public void setCapacityImpacts(List<RuleImpactItemVO> capacityImpacts) {
        this.capacityImpacts = capacityImpacts;
    }

    public List<RuleImpactItemVO> getFutureOnlyItems() {
        return futureOnlyItems;
    }

    public void setFutureOnlyItems(List<RuleImpactItemVO> futureOnlyItems) {
        this.futureOnlyItems = futureOnlyItems;
    }

    public Integer getManualCount() {
        return manualCount;
    }

    public void setManualCount(Integer manualCount) {
        this.manualCount = manualCount;
    }

    public Integer getCapacityExceededCount() {
        return capacityExceededCount;
    }

    public void setCapacityExceededCount(Integer capacityExceededCount) {
        this.capacityExceededCount = capacityExceededCount;
    }

    public Boolean getNoImpact() {
        return noImpact;
    }

    public void setNoImpact(Boolean noImpact) {
        this.noImpact = noImpact;
    }
}
