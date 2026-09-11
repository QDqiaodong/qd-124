package com.bracket.vo;

/**
 * 改挂预检结果：在普通绑定校验结果基础上补充源设备信息。
 * currentCount/availableSlots 等容量字段均针对目标封口机当前占用。
 */
public class RehangCheckResultVO extends BindCheckResultVO {

    private Long sourceEquipmentId;
    private String sourceEquipmentCode;
    private String sourceEquipmentName;

    public RehangCheckResultVO() {
    }

    public Long getSourceEquipmentId() {
        return sourceEquipmentId;
    }

    public void setSourceEquipmentId(Long sourceEquipmentId) {
        this.sourceEquipmentId = sourceEquipmentId;
    }

    public String getSourceEquipmentCode() {
        return sourceEquipmentCode;
    }

    public void setSourceEquipmentCode(String sourceEquipmentCode) {
        this.sourceEquipmentCode = sourceEquipmentCode;
    }

    public String getSourceEquipmentName() {
        return sourceEquipmentName;
    }

    public void setSourceEquipmentName(String sourceEquipmentName) {
        this.sourceEquipmentName = sourceEquipmentName;
    }
}
