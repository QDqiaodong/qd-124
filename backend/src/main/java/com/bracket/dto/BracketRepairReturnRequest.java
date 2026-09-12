package com.bracket.dto;

/**
 * 回库登记请求：返修完成后为返修单写下回库结论与检验人。
 * 结论合格后该支架才能再次用于批量挂接/换线改挂；结论不合格则继续拦截。
 */
public class BracketRepairReturnRequest {

    /** 回库结论：true=合格，false=不合格，必填 */
    private Boolean returnResult;
    /** 回库时间，空取当前时间 */
    private java.time.LocalDateTime returnTime;
    /** 检验人，必填 */
    private String inspector;
    /** 回库备注 */
    private String returnRemark;

    public Boolean getReturnResult() {
        return returnResult;
    }

    public void setReturnResult(Boolean returnResult) {
        this.returnResult = returnResult;
    }

    public java.time.LocalDateTime getReturnTime() {
        return returnTime;
    }

    public void setReturnTime(java.time.LocalDateTime returnTime) {
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
}
