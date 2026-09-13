package com.bracket.dto;

import java.time.LocalDateTime;

/**
 * 首件确认单退回再量请求：量差超线或品质异议时退回，重新测量需另开新单。
 */
public class FirstArticleReturnRequest {

    /** 退回人，必填 */
    private String operator;

    /** 退回原因（选填） */
    private String reason;

    /** 退回时间；为空时取服务端当前时间 */
    private LocalDateTime returnTime;

    public String getOperator() {
        return operator;
    }

    public void setOperator(String operator) {
        this.operator = operator;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public LocalDateTime getReturnTime() {
        return returnTime;
    }

    public void setReturnTime(LocalDateTime returnTime) {
        this.returnTime = returnTime;
    }
}
