package com.bracket.vo;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 当班润滑单视图；仅本班次最新一条且完成点数达标可作为当班放行依据。
 */
public class LubricationRecordVO {

    private Long id;
    private Long equipmentId;
    private String equipmentCode;
    private String equipmentName;
    private String lubricator;
    private String oilGrade;
    private Integer completedPoints;
    private Integer requiredPoints;
    private LocalDateTime lubricationTime;
    private LocalDate shiftDate;
    private String shiftCode;
    private String shiftName;
    private String shiftKey;
    private String remark;
    private LocalDateTime createTime;
    /** 是否为本班次最新一张润滑单（唯一可作为当班放行依据的记录） */
    private Boolean current;
    /** 完成点数是否达到该机当班要求 */
    private Boolean pointsSatisfied;

    public LubricationRecordVO() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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

    public LocalDateTime getLubricationTime() {
        return lubricationTime;
    }

    public void setLubricationTime(LocalDateTime lubricationTime) {
        this.lubricationTime = lubricationTime;
    }

    public LocalDate getShiftDate() {
        return shiftDate;
    }

    public void setShiftDate(LocalDate shiftDate) {
        this.shiftDate = shiftDate;
    }

    public String getShiftCode() {
        return shiftCode;
    }

    public void setShiftCode(String shiftCode) {
        this.shiftCode = shiftCode;
    }

    public String getShiftName() {
        return shiftName;
    }

    public void setShiftName(String shiftName) {
        this.shiftName = shiftName;
    }

    public String getShiftKey() {
        return shiftKey;
    }

    public void setShiftKey(String shiftKey) {
        this.shiftKey = shiftKey;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    public Boolean getCurrent() {
        return current;
    }

    public void setCurrent(Boolean current) {
        this.current = current;
    }

    public Boolean getPointsSatisfied() {
        return pointsSatisfied;
    }

    public void setPointsSatisfied(Boolean pointsSatisfied) {
        this.pointsSatisfied = pointsSatisfied;
    }
}
