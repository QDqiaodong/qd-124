package com.bracket.vo;

import java.time.LocalDateTime;

public class BracketVO {

    private Long id;
    private String name;
    private String model;
    private Double length;
    private Double width;
    private Long equipmentId;
    private String equipmentName;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    /** 当前返修单 ID（最新一张返修单），从未返修时为空 */
    private Long currentRepairId;
    /** 返修单号，未返修时为空 */
    private String currentRepairNo;
    /** 返修状态：REPAIRING 返修中 / RETURNED_QUALIFIED 已回库合格 / RETURNED_UNQUALIFIED 已回库不合格，未返修时为空 */
    private String repairStatus;
    /** 回库结论：true=合格，false=不合格，返修中/未返修时为空 */
    private Boolean returnResult;
    /** 最近一次回库检验人，未回库时为空 */
    private String inspector;

    public BracketVO() {
    }

    public BracketVO(Long id, String name, String model, Double length, Double width, Long equipmentId, String equipmentName, LocalDateTime createTime, LocalDateTime updateTime) {
        this.id = id;
        this.name = name;
        this.model = model;
        this.length = length;
        this.width = width;
        this.equipmentId = equipmentId;
        this.equipmentName = equipmentName;
        this.createTime = createTime;
        this.updateTime = updateTime;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public Double getLength() {
        return length;
    }

    public void setLength(Double length) {
        this.length = length;
    }

    public Double getWidth() {
        return width;
    }

    public void setWidth(Double width) {
        this.width = width;
    }

    public Long getEquipmentId() {
        return equipmentId;
    }

    public void setEquipmentId(Long equipmentId) {
        this.equipmentId = equipmentId;
    }

    public String getEquipmentName() {
        return equipmentName;
    }

    public void setEquipmentName(String equipmentName) {
        this.equipmentName = equipmentName;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    public LocalDateTime getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(LocalDateTime updateTime) {
        this.updateTime = updateTime;
    }

    public Long getCurrentRepairId() {
        return currentRepairId;
    }

    public void setCurrentRepairId(Long currentRepairId) {
        this.currentRepairId = currentRepairId;
    }

    public String getCurrentRepairNo() {
        return currentRepairNo;
    }

    public void setCurrentRepairNo(String currentRepairNo) {
        this.currentRepairNo = currentRepairNo;
    }

    public String getRepairStatus() {
        return repairStatus;
    }

    public void setRepairStatus(String repairStatus) {
        this.repairStatus = repairStatus;
    }

    public Boolean getReturnResult() {
        return returnResult;
    }

    public void setReturnResult(Boolean returnResult) {
        this.returnResult = returnResult;
    }

    public String getInspector() {
        return inspector;
    }

    public void setInspector(String inspector) {
        this.inspector = inspector;
    }
}
