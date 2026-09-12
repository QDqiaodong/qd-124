package com.bracket.dto;

import java.time.LocalDateTime;

/**
 * 当班润滑登记请求：当班开工时为封口机写下润滑人、油品与完成点数。
 */
public class LubricationRegisterRequest {

    /** 润滑人，必填 */
    private String lubricator;

    /** 油品（牌号/名称），必填 */
    private String oilGrade;

    /** 本次完成润滑点数，必填，必须为正数且不大于该机要求点数 */
    private Integer completedPoints;

    /** 润滑时间；为空时取服务端当前时间（用于归属当班） */
    private LocalDateTime lubricationTime;

    /** 备注（选填） */
    private String remark;

    public String getLubricator() {
        return lubricator;
    }

    public void setLubricator(String lubricator) {
        this.lubricator = lubricator;
    }

    public String getOilGrade() {
        return oilGrade;
    }

    public void setOilGrade(String oilGrade) {
        this.oilGrade = oilGrade;
    }

    public Integer getCompletedPoints() {
        return completedPoints;
    }

    public void setCompletedPoints(Integer completedPoints) {
        this.completedPoints = completedPoints;
    }

    public LocalDateTime getLubricationTime() {
        return lubricationTime;
    }

    public void setLubricationTime(LocalDateTime lubricationTime) {
        this.lubricationTime = lubricationTime;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }
}
