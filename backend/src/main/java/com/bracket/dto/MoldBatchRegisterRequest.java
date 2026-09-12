package com.bracket.dto;

import java.time.LocalDateTime;

/**
 * 换模批次登记请求：换模后为封口机写下当前模具批次。
 */
public class MoldBatchRegisterRequest {

    /** 模具批次号，必填 */
    private String batchNo;

    /** 模具型号，必填且必须在该设备允许模具型号清单内 */
    private String moldModel;

    /** 换模时间；为空时取服务端当前时间 */
    private LocalDateTime changeTime;

    /** 操作人（选填） */
    private String operator;

    /** 备注（选填） */
    private String remark;

    public String getBatchNo() {
        return batchNo;
    }

    public void setBatchNo(String batchNo) {
        this.batchNo = batchNo;
    }

    public String getMoldModel() {
        return moldModel;
    }

    public void setMoldModel(String moldModel) {
        this.moldModel = moldModel;
    }

    public LocalDateTime getChangeTime() {
        return changeTime;
    }

    public void setChangeTime(LocalDateTime changeTime) {
        this.changeTime = changeTime;
    }

    public String getOperator() {
        return operator;
    }

    public void setOperator(String operator) {
        this.operator = operator;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }
}
