package com.bracket.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 当班润滑台账记录：当班开工时为封口机登记润滑人、油品与完成点数。
 * 一台设备按润滑时间倒序保留全部历史；仅「本班次最新一条且完成点数达标」的记录
 * 可作为批量挂接/换线改挂的放行依据，换班后上一班记录只作历史追溯，不再放行当班挂接。
 */
@Entity
@Table(name = "lubrication_record",
        indexes = {
                @Index(name = "idx_lub_equipment", columnList = "equipment_id"),
                @Index(name = "idx_lub_shift", columnList = "shift_key"),
                @Index(name = "idx_lub_time", columnList = "lubrication_time")
        })
public class LubricationRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "equipment_id", nullable = false)
    private Long equipmentId;

    /** 润滑人，当班开工登记必填 */
    @Column(name = "lubricator", nullable = false, length = 100)
    private String lubricator;

    /** 油品（牌号/名称），必填 */
    @Column(name = "oil_grade", nullable = false, length = 100)
    private String oilGrade;

    /** 本次完成润滑点数 */
    @Column(name = "completed_points", nullable = false)
    private Integer completedPoints;

    /** 当班润滑要求点数（登记时设备要求，冗余留痕） */
    @Column(name = "required_points")
    private Integer requiredPoints;

    /** 润滑时间（业务时间，由登记人确认，默认服务端当前时间），用于归属班次与历史排序 */
    @Column(name = "lubrication_time", nullable = false)
    private LocalDateTime lubricationTime;

    /** 班次归属日期 */
    @Column(name = "shift_date", nullable = false)
    private LocalDate shiftDate;

    /** 班次代码 */
    @Column(name = "shift_code", nullable = false, length = 20)
    private String shiftCode;

    /** 班次名称（早班/晚班/夜班） */
    @Column(name = "shift_name", nullable = false, length = 20)
    private String shiftName;

    /** 当班唯一标识 yyyy-MM-dd#班次代码，放行只比对本班次 */
    @Column(name = "shift_key", nullable = false, length = 40)
    private String shiftKey;

    @Column(name = "remark", length = 500)
    private String remark;

    @CreationTimestamp
    @Column(name = "create_time", updatable = false)
    private LocalDateTime createTime;

    public LubricationRecord() {
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

    public Integer getRequiredPoints() {
        return requiredPoints;
    }

    public void setRequiredPoints(Integer requiredPoints) {
        this.requiredPoints = requiredPoints;
    }

    public LocalDateTime getLubricationTime() {
        return lubricationTime;
    }

    public void setLubricationTime(LocalDateTime lubricationTime) {
        this.lubricationTime = lubricationTime;
    }

    public LocalDate getShiftDate() {
        return shiftDate;
    }

    public void setShiftDate(LocalDate shiftDate) {
        this.shiftDate = shiftDate;
    }

    public String getShiftCode() {
        return shiftCode;
    }

    public void setShiftCode(String shiftCode) {
        this.shiftCode = shiftCode;
    }

    public String getShiftName() {
        return shiftName;
    }

    public void setShiftName(String shiftName) {
        this.shiftName = shiftName;
    }

    public String getShiftKey() {
        return shiftKey;
    }

    public void setShiftKey(String shiftKey) {
        this.shiftKey = shiftKey;
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
