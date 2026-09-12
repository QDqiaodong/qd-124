package com.bracket.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "equipment")
public class Equipment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "equipment_code", nullable = false)
    private String equipmentCode;

    @Column(name = "equipment_name", nullable = false)
    private String equipmentName;

    @Column(name = "max_brackets")
    private Integer maxBrackets;

    @Column(name = "allowed_models", length = 1000)
    private String allowedModels;

    /** 允许在本机使用的模具型号清单，逗号分隔，空表示不维护清单（不允许任何批次登记）。 */
    @Column(name = "allowed_mold_models", length = 1000)
    private String allowedMoldModels;

    @Column(name = "min_length", precision = 10, scale = 2)
    private BigDecimal minLength;

    @Column(name = "max_length", precision = 10, scale = 2)
    private BigDecimal maxLength;

    @Column(name = "min_width", precision = 10, scale = 2)
    private BigDecimal minWidth;

    @Column(name = "max_width", precision = 10, scale = 2)
    private BigDecimal maxWidth;

    @CreationTimestamp
    @Column(name = "create_time", updatable = false)
    private LocalDateTime createTime;

    @UpdateTimestamp
    @Column(name = "update_time")
    private LocalDateTime updateTime;

    public Equipment() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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

    public Integer getMaxBrackets() {
        return maxBrackets;
    }

    public void setMaxBrackets(Integer maxBrackets) {
        this.maxBrackets = maxBrackets;
    }

    public String getAllowedModels() {
        return allowedModels;
    }

    public void setAllowedModels(String allowedModels) {
        this.allowedModels = allowedModels;
    }

    public String getAllowedMoldModels() {
        return allowedMoldModels;
    }

    public void setAllowedMoldModels(String allowedMoldModels) {
        this.allowedMoldModels = allowedMoldModels;
    }

    public BigDecimal getMinLength() {
        return minLength;
    }

    public void setMinLength(BigDecimal minLength) {
        this.minLength = minLength;
    }

    public BigDecimal getMaxLength() {
        return maxLength;
    }

    public void setMaxLength(BigDecimal maxLength) {
        this.maxLength = maxLength;
    }

    public BigDecimal getMinWidth() {
        return minWidth;
    }

    public void setMinWidth(BigDecimal minWidth) {
        this.minWidth = minWidth;
    }

    public BigDecimal getMaxWidth() {
        return maxWidth;
    }

    public void setMaxWidth(BigDecimal maxWidth) {
        this.maxWidth = maxWidth;
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
}
