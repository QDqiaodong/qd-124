package com.bracket.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * 换模批次记录：每次换模后为封口机登记当前模具批次。
 * 一台设备按换模时间倒序保留全部历史；仅最新一条代表「当前批次」，
 * 作为批量挂接/换线改挂的放行依据，旧记录只作历史追溯，不再放行。
 */
@Entity
@Table(name = "mold_batch_record",
        indexes = {
                @Index(name = "idx_mold_batch_equipment", columnList = "equipment_id"),
                @Index(name = "idx_mold_batch_change_time", columnList = "change_time")
        })
public class MoldBatchRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "equipment_id", nullable = false)
    private Long equipmentId;

    /** 模具批次号，换模后必须登记 */
    @Column(name = "batch_no", nullable = false, length = 100)
    private String batchNo;

    /** 模具型号，必须在该设备允许模具型号清单内 */
    @Column(name = "mold_model", nullable = false, length = 100)
    private String moldModel;

    /** 换模时间（业务时间，由登记人确认），用于区分当前批次与历史批次 */
    @Column(name = "change_time", nullable = false)
    private LocalDateTime changeTime;

    @Column(name = "operator", length = 100)
    private String operator;

    @Column(name = "remark", length = 500)
    private String remark;

    @CreationTimestamp
    @Column(name = "create_time", updatable = false)
    private LocalDateTime createTime;

    public MoldBatchRecord() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getEquipmentId() {
        return equipmentId;
    }

    public void setEquipmentId(Long equipmentId) {
        this.equipmentId = equipmentId;
    }

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

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }
}
