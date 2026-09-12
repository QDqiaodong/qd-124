package com.bracket.vo;

import java.time.LocalDateTime;

/**
 * 支架返修单视图；最新一张即该支架当前返修状态。
 */
public class BracketRepairRecordVO {

    private Long id;
    private Long bracketId;
    private String bracketName;
    private String bracketModel;
    private String repairNo;
    private String repairReason;
    private LocalDateTime repairTime;
    private String repairOperator;
    /** 回库结论：true=合格，false=不合格，未回库时为空 */
    private Boolean returnResult;
    private LocalDateTime returnTime;
    private String inspector;
    private String returnRemark;
    private LocalDateTime createTime;
    /** 是否为该支架最新（当前）返修单：仅当前返修单可作为放行依据 */
    private Boolean current;
    /** 返修状态：REPAIRING 返修中（未回库）/ RETURNED_QUALIFIED 已回库合格 / RETURNED_UNQUALIFIED 已回库不合格 */
    private String status;

    public BracketRepairRecordVO() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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

    public String getBracketModel() {
        return bracketModel;
    }

    public void setBracketModel(String bracketModel) {
        this.bracketModel = bracketModel;
    }

    public String getRepairNo() {
        return repairNo;
    }

    public void setRepairNo(String repairNo) {
        this.repairNo = repairNo;
    }

    public String getRepairReason() {
        return repairReason;
    }

    public void setRepairReason(String repairReason) {
        this.repairReason = repairReason;
    }

    public LocalDateTime getRepairTime() {
        return repairTime;
    }

    public void setRepairTime(LocalDateTime repairTime) {
        this.repairTime = repairTime;
    }

    public String getRepairOperator() {
        return repairOperator;
    }

    public void setRepairOperator(String repairOperator) {
        this.repairOperator = repairOperator;
    }

    public Boolean getReturnResult() {
        return returnResult;
    }

    public void setReturnResult(Boolean returnResult) {
        this.returnResult = returnResult;
    }

    public LocalDateTime getReturnTime() {
        return returnTime;
    }

    public void setReturnTime(LocalDateTime returnTime) {
        this.returnTime = returnTime;
    }

    public String getInspector() {
        return inspector;
    }

    public void setInspector(String inspector) {
        this.inspector = inspector;
    }

    public String getReturnRemark() {
        return returnRemark;
    }

    public void setReturnRemark(String returnRemark) {
        this.returnRemark = returnRemark;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    public Boolean getCurrent() {
        return current;
    }

    public void setCurrent(Boolean current) {
        this.current = current;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
