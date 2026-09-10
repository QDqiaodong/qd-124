package com.bracket.vo;

import java.util.List;

public class EquipmentVO {

    private Long id;
    private String code;
    private String name;
    private Integer bracketCount;
    private Integer maxBrackets;
    private List<String> allowedModels;
    private Double minLength;
    private Double maxLength;
    private Double minWidth;
    private Double maxWidth;
    /** 是否已配置配套规则 */
    private Boolean ruleConfigured;
    /** 容量状态：normal 正常 / full 已满 / exceeded 超出容量 / unlimited 未限制 */
    private String capacityStatus;

    public EquipmentVO() {
    }

    public EquipmentVO(Long id, String code, String name, Integer bracketCount) {
        this.id = id;
        this.code = code;
        this.name = name;
        this.bracketCount = bracketCount;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Integer getBracketCount() {
        return bracketCount;
    }

    public void setBracketCount(Integer bracketCount) {
        this.bracketCount = bracketCount;
    }

    public Integer getMaxBrackets() {
        return maxBrackets;
    }

    public void setMaxBrackets(Integer maxBrackets) {
        this.maxBrackets = maxBrackets;
    }

    public List<String> getAllowedModels() {
        return allowedModels;
    }

    public void setAllowedModels(List<String> allowedModels) {
        this.allowedModels = allowedModels;
    }

    public Double getMinLength() {
        return minLength;
    }

    public void setMinLength(Double minLength) {
        this.minLength = minLength;
    }

    public Double getMaxLength() {
        return maxLength;
    }

    public void setMaxLength(Double maxLength) {
        this.maxLength = maxLength;
    }

    public Double getMinWidth() {
        return minWidth;
    }

    public void setMinWidth(Double minWidth) {
        this.minWidth = minWidth;
    }

    public Double getMaxWidth() {
        return maxWidth;
    }

    public void setMaxWidth(Double maxWidth) {
        this.maxWidth = maxWidth;
    }

    public Boolean getRuleConfigured() {
        return ruleConfigured;
    }

    public void setRuleConfigured(Boolean ruleConfigured) {
        this.ruleConfigured = ruleConfigured;
    }

    public String getCapacityStatus() {
        return capacityStatus;
    }

    public void setCapacityStatus(String capacityStatus) {
        this.capacityStatus = capacityStatus;
    }
}
