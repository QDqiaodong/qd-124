package com.bracket.vo;

import java.util.List;

/**
 * 规则变更影响的单条已绑定支架诊断结果。
 */
public class RuleImpactItemVO {

    private Long bracketId;
    private String bracketName;
    private String model;
    private Double length;
    private Double width;
    /**
     * 处置级别：
     * existing_violation 存量绑定在新规则下不再合规，需要人工处理；
     * capacity_only 支架本身仍符合型号/尺寸规则，仅受容量收紧影响；
     * future_only 支架在新旧规则下均合规，仅影响后续绑定。
     */
    private String impactType;
    /** 具体冲突原因；仅影响后续绑定时为空 */
    private List<String> reasons;

    public RuleImpactItemVO() {
    }

    public RuleImpactItemVO(Long bracketId, String bracketName, String model, Double length, Double width,
                            String impactType, List<String> reasons) {
        this.bracketId = bracketId;
        this.bracketName = bracketName;
        this.model = model;
        this.length = length;
        this.width = width;
        this.impactType = impactType;
        this.reasons = reasons;
    }

    public Long getBracketId() {
        return bracketId;
    }

    public void setBracketId(Long bracketId) {
        this.bracketId = bracketId;
    }

    public String getBracketName() {
        return bracketName;
    }

    public void setBracketName(String bracketName) {
        this.bracketName = bracketName;
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

    public String getImpactType() {
        return impactType;
    }

    public void setImpactType(String impactType) {
        this.impactType = impactType;
    }

    public List<String> getReasons() {
        return reasons;
    }

    public void setReasons(List<String> reasons) {
        this.reasons = reasons;
    }
}
