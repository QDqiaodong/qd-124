package com.bracket.dto;

import java.math.BigDecimal;

/**
 * 首件尺寸确认单开单请求：换完模具准备放量时，调度为机台当前模具批次开单。
 * 模具批次不传入，由后端取该机当前（最新）批次并快照留痕。
 */
public class FirstArticleCreateRequest {

    /** 机台（封口设备）ID，必填 */
    private Long equipmentId;

    /** 标准长（mm），必填 */
    private BigDecimal standardLength;

    /** 标准宽（mm），必填 */
    private BigDecimal standardWidth;

    /** 标准高（mm），必填 */
    private BigDecimal standardHeight;

    /** 公差（±mm），必填且大于 0，三个维度共用 */
    private BigDecimal tolerance;

    /** 实测长（mm），必填 */
    private BigDecimal measuredLength;

    /** 实测宽（mm），必填 */
    private BigDecimal measuredWidth;

    /** 实测高（mm），必填 */
    private BigDecimal measuredHeight;

    /** 开单人（调度），必填 */
    private String operator;

    /** 备注（选填） */
    private String remark;

    public Long getEquipmentId() {
        return equipmentId;
    }

    public void setEquipmentId(Long equipmentId) {
        this.equipmentId = equipmentId;
    }

    public BigDecimal getStandardLength() {
        return standardLength;
    }

    public void setStandardLength(BigDecimal standardLength) {
        this.standardLength = standardLength;
    }

    public BigDecimal getStandardWidth() {
        return standardWidth;
    }

    public void setStandardWidth(BigDecimal standardWidth) {
        this.standardWidth = standardWidth;
    }

    public BigDecimal getStandardHeight() {
        return standardHeight;
    }

    public void setStandardHeight(BigDecimal standardHeight) {
        this.standardHeight = standardHeight;
    }

    public BigDecimal getTolerance() {
        return tolerance;
    }

    public void setTolerance(BigDecimal tolerance) {
        this.tolerance = tolerance;
    }

    public BigDecimal getMeasuredLength() {
        return measuredLength;
    }

    public void setMeasuredLength(BigDecimal measuredLength) {
        this.measuredLength = measuredLength;
    }

    public BigDecimal getMeasuredWidth() {
        return measuredWidth;
    }

    public void setMeasuredWidth(BigDecimal measuredWidth) {
        this.measuredWidth = measuredWidth;
    }

    public BigDecimal getMeasuredHeight() {
        return measuredHeight;
    }

    public void setMeasuredHeight(BigDecimal measuredHeight) {
        this.measuredHeight = measuredHeight;
    }

    public String getOperator() {
        return operator;
    }

    public void setOperator(String operator) {
        this.operator = operator;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }
}
