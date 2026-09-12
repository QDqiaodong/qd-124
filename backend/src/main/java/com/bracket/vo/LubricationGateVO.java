package com.bracket.vo;

/**
 * 当班润滑放行闸门：批量挂接、换线改挂前对目标封口机当班润滑台账的硬联锁结论。
 * 未维护要求点数、本班未登记润滑单、或本班最新一单完成点数不够时 passed=false，整单拦截。
 * 放行只认本班次记录：换班后上一班润滑记录永不作为当班放行依据。
 */
public class LubricationGateVO {

    /** 是否放行：本班已登记且完成点数达到要求 */
    private Boolean passed;
    /** 拦截原因；放行时为空 */
    private String reason;
    /** 当班最新润滑单 ID，本班未登记时为空 */
    private Long currentRecordId;
    /** 润滑人 */
    private String lubricator;
    /** 油品 */
    private String oilGrade;
    /** 完成点数 */
    private Integer completedPoints;
    /** 设备当班要求点数 */
    private Integer requiredPoints;
    /** 服务端当前班次标签，如 2026-09-12 早班 */
    private String currentShiftLabel;
    /** 当班最新润滑单所属班次标签，本班无记录时为空 */
    private String recordShiftLabel;

    public LubricationGateVO() {
    }

    public LubricationGateVO(Boolean passed, String reason, Long currentRecordId, String lubricator,
                             String oilGrade, Integer completedPoints, Integer requiredPoints,
                             String currentShiftLabel, String recordShiftLabel) {
        this.passed = passed;
        this.reason = reason;
        this.currentRecordId = currentRecordId;
        this.lubricator = lubricator;
        this.oilGrade = oilGrade;
        this.completedPoints = completedPoints;
        this.requiredPoints = requiredPoints;
        this.currentShiftLabel = currentShiftLabel;
        this.recordShiftLabel = recordShiftLabel;
    }

    public Boolean getPassed() {
        return passed;
    }

    public void setPassed(Boolean passed) {
        this.passed = passed;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public Long getCurrentRecordId() {
        return currentRecordId;
    }

    public void setCurrentRecordId(Long currentRecordId) {
        this.currentRecordId = currentRecordId;
    }

    public String getLubricator() {
        return lubricator;
    }

    public void setLubricator(String lubricator) {
        this.lubricator = lubricator;
    }

    public String getOilGrade() {
        return oilGrade;
    }

    public void setOilGrade(String oilGrade) {
        this.oilGrade = oilGrade;
    }

    public Integer getCompletedPoints() {
        return completedPoints;
    }

    public void setCompletedPoints(Integer completedPoints) {
        this.completedPoints = completedPoints;
    }

    public Integer getRequiredPoints() {
        return requiredPoints;
    }

    public void setRequiredPoints(Integer requiredPoints) {
        this.requiredPoints = requiredPoints;
    }

    public String getCurrentShiftLabel() {
        return currentShiftLabel;
    }

    public void setCurrentShiftLabel(String currentShiftLabel) {
        this.currentShiftLabel = currentShiftLabel;
    }

    public String getRecordShiftLabel() {
        return recordShiftLabel;
    }

    public void setRecordShiftLabel(String recordShiftLabel) {
        this.recordShiftLabel = recordShiftLabel;
    }
}
