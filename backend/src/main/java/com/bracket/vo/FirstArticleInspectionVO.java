package com.bracket.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 首件尺寸确认单视图：列表与详情共用，含机台、模具批次快照、标准/实测尺寸、
 * 量差与超线标记、放行结果，以及开单/签放/退回的签字人与时间。
 */
public class FirstArticleInspectionVO {

    private Long id;
    private String formNo;
    private Long equipmentId;
    private String equipmentCode;
    private String equipmentName;
    private Long moldBatchRecordId;
    private String batchNo;
    private String moldModel;
    private BigDecimal standardLength;
    private BigDecimal standardWidth;
    private BigDecimal standardHeight;
    private BigDecimal tolerance;
    private BigDecimal measuredLength;
    private BigDecimal measuredWidth;
    private BigDecimal measuredHeight;
    private BigDecimal lengthDeviation;
    private BigDecimal widthDeviation;
    private BigDecimal heightDeviation;
    /** 是否超线：true 时禁止放行，只能退回再量 */
    private boolean outOfTolerance;
    /** 放行结果：PENDING 待签放 / RELEASED 已放行 / RETURNED 已退回再量 */
    private String status;
    private String operator;
    private String remark;
    private String releaseSigner;
    private LocalDateTime releaseTime;
    private String returnOperator;
    private String returnReason;
    private LocalDateTime returnTime;
    private LocalDateTime createTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getFormNo() {
        return formNo;
    }

    public void setFormNo(String formNo) {
        this.formNo = formNo;
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

    public Long getMoldBatchRecordId() {
        return moldBatchRecordId;
    }

    public void setMoldBatchRecordId(Long moldBatchRecordId) {
        this.moldBatchRecordId = moldBatchRecordId;
    }

    public String getBatchNo() {
        return batchNo;
    }

    public void setBatchNo(String batchNo) {
        this.batchNo = batchNo;
    }

    public String getMoldModel() {
        return moldModel;
    }

    public void setMoldModel(String moldModel) {
        this.moldModel = moldModel;
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

    public BigDecimal getLengthDeviation() {
        return lengthDeviation;
    }

    public void setLengthDeviation(BigDecimal lengthDeviation) {
        this.lengthDeviation = lengthDeviation;
    }

    public BigDecimal getWidthDeviation() {
        return widthDeviation;
    }

    public void setWidthDeviation(BigDecimal widthDeviation) {
        this.widthDeviation = widthDeviation;
    }

    public BigDecimal getHeightDeviation() {
        return heightDeviation;
    }

    public void setHeightDeviation(BigDecimal heightDeviation) {
        this.heightDeviation = heightDeviation;
    }

    public boolean isOutOfTolerance() {
        return outOfTolerance;
    }

    public void setOutOfTolerance(boolean outOfTolerance) {
        this.outOfTolerance = outOfTolerance;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
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

    public String getReleaseSigner() {
        return releaseSigner;
    }

    public void setReleaseSigner(String releaseSigner) {
        this.releaseSigner = releaseSigner;
    }

    public LocalDateTime getReleaseTime() {
        return releaseTime;
    }

    public void setReleaseTime(LocalDateTime releaseTime) {
        this.releaseTime = releaseTime;
    }

    public String getReturnOperator() {
        return returnOperator;
    }

    public void setReturnOperator(String returnOperator) {
        this.returnOperator = returnOperator;
    }

    public String getReturnReason() {
        return returnReason;
    }

    public void setReturnReason(String returnReason) {
        this.returnReason = returnReason;
    }

    public LocalDateTime getReturnTime() {
        return returnTime;
    }

    public void setReturnTime(LocalDateTime returnTime) {
        this.returnTime = returnTime;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }
}
