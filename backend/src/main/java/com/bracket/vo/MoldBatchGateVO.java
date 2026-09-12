package com.bracket.vo;

/**
 * 换模批次放行闸门：批量挂接、换线改挂前对目标封口机当前模具批次的硬联锁结论。
 * 未写当前批次、或当前批次模具型号不在设备允许清单内时 passed=false，整单拦截。
 * 放行只认当前（最新）批次，历史换模记录永不作为放行依据。
 */
public class MoldBatchGateVO {

    /** 是否放行：当前批次已登记且型号在允许清单内 */
    private Boolean passed;
    /** 拦截原因；放行时为空 */
    private String reason;
    /** 当前批次 ID，未登记时为空 */
    private Long currentBatchId;
    /** 当前模具批次号，未登记时为空 */
    private String currentBatchNo;
    /** 当前模具型号，未登记时为空 */
    private String currentMoldModel;

    public MoldBatchGateVO() {
    }

    public MoldBatchGateVO(Boolean passed, String reason, Long currentBatchId,
                           String currentBatchNo, String currentMoldModel) {
        this.passed = passed;
        this.reason = reason;
        this.currentBatchId = currentBatchId;
        this.currentBatchNo = currentBatchNo;
        this.currentMoldModel = currentMoldModel;
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
}
