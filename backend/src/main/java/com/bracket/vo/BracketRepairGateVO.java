package com.bracket.vo;

import java.time.LocalDateTime;

/**
 * 支架返修放行闸门：评估单个支架能否再次批量挂接/换线改挂。
 * 仅最新一张返修单可作为放行依据：
 * - 返修中（未写回库结论/检验人）：拦截；
 * - 已回库但结论不合格：拦截；
 * - 已回库且结论合格：放行。
 * 从未返修的支架不经过本闸门（本对象为 null）。
 */
public class BracketRepairGateVO {

    private Boolean passed;
    private String reason;
    private Long repairRecordId;
    private String repairNo;
    /** REPAIRING / RETURNED_QUALIFIED / RETURNED_UNQUALIFIED */
    private String status;
    private Boolean returnResult;
    private String inspector;
    private LocalDateTime returnTime;

    public BracketRepairGateVO() {
    }

    public BracketRepairGateVO(Boolean passed, String reason, Long repairRecordId, String repairNo,
                               String status, Boolean returnResult, String inspector, LocalDateTime returnTime) {
        this.passed = passed;
        this.reason = reason;
        this.repairRecordId = repairRecordId;
        this.repairNo = repairNo;
        this.status = status;
        this.returnResult = returnResult;
        this.inspector = inspector;
        this.returnTime = returnTime;
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

    public Long getRepairRecordId() {
        return repairRecordId;
    }

    public void setRepairRecordId(Long repairRecordId) {
        this.repairRecordId = repairRecordId;
    }

    public String getRepairNo() {
        return repairNo;
    }

    public void setRepairNo(String repairNo) {
        this.repairNo = repairNo;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
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

    public LocalDateTime getReturnTime() {
        return returnTime;
    }

    public void setReturnTime(LocalDateTime returnTime) {
        this.returnTime = returnTime;
    }
}
