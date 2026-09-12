package com.bracket.vo;

public class BindCheckItemVO {

    private Long bracketId;
    private String bracketName;
    private String model;
    private Double length;
    private Double width;
    /** 是否通过校验 */
    private Boolean passed;
    /** 冲突原因，通过时为空 */
    private String reason;
    /**
     * 返修放行闸门（按支架逐条判定）：返修中未回库/回库不合格时不通过，该条判为冲突。
     * 从未返修的支架该字段为空，不经过返修闸门。
     */
    private BracketRepairGateVO repairGate;

    public BindCheckItemVO() {
    }

    public BindCheckItemVO(Long bracketId, String bracketName, String model, Double length, Double width,
                           Boolean passed, String reason) {
        this.bracketId = bracketId;
        this.bracketName = bracketName;
        this.model = model;
        this.length = length;
        this.width = width;
        this.passed = passed;
        this.reason = reason;
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

    public BracketRepairGateVO getRepairGate() {
        return repairGate;
    }

    public void setRepairGate(BracketRepairGateVO repairGate) {
        this.repairGate = repairGate;
    }
}
