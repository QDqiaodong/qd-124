package com.bracket.dto;

/**
 * 送修登记请求：解绑后的支架标记为返修，创建返修单（此时尚未回库）。
 */
public class BracketRepairCreateRequest {

    /** 返修单号 */
    private String repairNo;
    /** 送修原因/故障描述 */
    private String repairReason;
    /** 送修时间，空取当前时间 */
    private java.time.LocalDateTime repairTime;
    /** 送修人 */
    private String repairOperator;

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

    public java.time.LocalDateTime getRepairTime() {
        return repairTime;
    }

    public void setRepairTime(java.time.LocalDateTime repairTime) {
        this.repairTime = repairTime;
    }

    public String getRepairOperator() {
        return repairOperator;
    }

    public void setRepairOperator(String repairOperator) {
        this.repairOperator = repairOperator;
    }
}
