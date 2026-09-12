package com.bracket.dto;

public class EquipmentRuleRequest {

    /** 最大支架数量，空表示不限制 */
    private Integer maxBrackets;

    /** 允许型号，逗号分隔；空表示不限制 */
    private String allowedModels;

    /** 允许模具型号，逗号分隔；换模登记的批次模具型号必须在此清单内 */
    private String allowedMoldModels;

    /** 最小长度(mm)，空表示不限制 */
    private Double minLength;

    /** 最大长度(mm)，空表示不限制 */
    private Double maxLength;

    /** 最小宽度(mm)，空表示不限制 */
    private Double minWidth;

    /** 最大宽度(mm)，空表示不限制 */
    private Double maxWidth;

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
}
