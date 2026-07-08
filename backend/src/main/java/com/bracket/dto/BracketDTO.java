package com.bracket.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class BracketDTO {

    private Long id;
    private String name;
    private String model;
    private BigDecimal lengthMm;
    private BigDecimal widthMm;
    private Long equipmentId;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private String equipmentCode;
    private String equipmentName;

    public BracketDTO() {
    }

    public BracketDTO(Long id, String name, String model, BigDecimal lengthMm, BigDecimal widthMm, Long equipmentId, LocalDateTime createTime, LocalDateTime updateTime, String equipmentCode, String equipmentName) {
        this.id = id;
        this.name = name;
        this.model = model;
        this.lengthMm = lengthMm;
        this.widthMm = widthMm;
        this.equipmentId = equipmentId;
        this.createTime = createTime;
        this.updateTime = updateTime;
        this.equipmentCode = equipmentCode;
        this.equipmentName = equipmentName;
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

    public BigDecimal getLengthMm() {
        return lengthMm;
    }

    public void setLengthMm(BigDecimal lengthMm) {
        this.lengthMm = lengthMm;
    }

    public BigDecimal getWidthMm() {
        return widthMm;
    }

    public void setWidthMm(BigDecimal widthMm) {
        this.widthMm = widthMm;
    }

    public Long getEquipmentId() {
        return equipmentId;
    }

    public void setEquipmentId(Long equipmentId) {
        this.equipmentId = equipmentId;
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
}
