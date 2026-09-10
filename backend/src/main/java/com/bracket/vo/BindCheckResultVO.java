package com.bracket.vo;

import java.util.List;

/**
 * 绑定前配套规则校验结果。
 */
public class BindCheckResultVO {

    private Long equipmentId;
    private String equipmentCode;
    private String equipmentName;
    /** 设备当前已占用容量 */
    private Integer currentCount;
    /** 设备最大支架数量，空表示不限制 */
    private Integer maxBrackets;
    /** 本次校验可用容量，空表示不限制 */
    private Integer availableSlots;
    private List<BindCheckItemVO> items;
    private List<BindCheckItemVO> passedItems;
    private List<BindCheckItemVO> conflicts;

    public BindCheckResultVO() {
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

    public Integer getAvailableSlots() {
        return availableSlots;
    }

    public void setAvailableSlots(Integer availableSlots) {
        this.availableSlots = availableSlots;
    }

    public List<BindCheckItemVO> getItems() {
        return items;
    }

    public void setItems(List<BindCheckItemVO> items) {
        this.items = items;
    }

    public List<BindCheckItemVO> getPassedItems() {
        return passedItems;
    }

    public void setPassedItems(List<BindCheckItemVO> passedItems) {
        this.passedItems = passedItems;
    }

    public List<BindCheckItemVO> getConflicts() {
        return conflicts;
    }

    public void setConflicts(List<BindCheckItemVO> conflicts) {
        this.conflicts = conflicts;
    }
}
