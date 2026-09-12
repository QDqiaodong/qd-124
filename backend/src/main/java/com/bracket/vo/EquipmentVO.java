package com.bracket.vo;

import java.time.LocalDateTime;
import java.util.List;

public class EquipmentVO {

    private Long id;
    private String code;
    private String name;
    private Integer bracketCount;
    private Integer maxBrackets;
    private List<String> allowedModels;
    /** 允许在本机登记使用的模具型号清单 */
    private List<String> allowedMoldModels;
    private Double minLength;
    private Double maxLength;
    private Double minWidth;
    private Double maxWidth;
    /** 是否已配置配套规则 */
    private Boolean ruleConfigured;
    /** 容量状态：normal 正常 / full 已满 / exceeded 超出容量 / unlimited 未限制 */
    private String capacityStatus;
    /** 当前模具批次 ID（最新一次换模登记），未登记时为空 */
    private Long currentBatchId;
    /** 当前模具批次号，未登记时为空 */
    private String currentBatchNo;
    /** 当前模具型号，未登记时为空 */
    private String currentMoldModel;
    /** 当前批次换模时间 */
    private LocalDateTime currentBatchChangeTime;
    /** 换模批次放行是否就绪：已登记当前批次且型号在允许清单内 */
    private Boolean moldBatchReady;

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

    public List<String> getAllowedMoldModels() {
        return allowedMoldModels;
    }

    public void setAllowedMoldModels(List<String> allowedMoldModels) {
        this.allowedMoldModels = allowedMoldModels;
    }

    public Long getCurrentBatchId() {
        return currentBatchId;
    }

    public void setCurrentBatchId(Long currentBatchId) {
        this.currentBatchId = currentBatchId;
    }

    public String getCurrentBatchNo() {
        return currentBatchNo;
    }

    public void setCurrentBatchNo(String currentBatchNo) {
        this.currentBatchNo = currentBatchNo;
    }

    public String getCurrentMoldModel() {
        return currentMoldModel;
    }

    public void setCurrentMoldModel(String currentMoldModel) {
        this.currentMoldModel = currentMoldModel;
    }

    public LocalDateTime getCurrentBatchChangeTime() {
        return currentBatchChangeTime;
    }

    public void setCurrentBatchChangeTime(LocalDateTime currentBatchChangeTime) {
        this.currentBatchChangeTime = currentBatchChangeTime;
    }

    public Boolean getMoldBatchReady() {
        return moldBatchReady;
    }

    public void setMoldBatchReady(Boolean moldBatchReady) {
        this.moldBatchReady = moldBatchReady;
    }
}
