package com.bracket.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 首件尺寸确认单：换完模具准备放量时，由调度为机台当前模具批次开单，
 * 写清标准/实测长宽高与公差，系统按「量差 = |实测 - 标准| 是否超过公差」判定超线。
 *
 * 关键约束：
 * 1. 开单时机台必须已登记当前模具批次（批次号/型号快照留痕，换批次后历史单仍可追溯）；
 * 2. 量差超线的单永远不能放行量产，只能退回再量（退回后重新开单测量）；
 * 3. 放行/退回都必须写签字人与签字时间，关掉页面记录仍在库中可查。
 */
@Entity
@Table(name = "first_article_inspection",
        indexes = {
                @Index(name = "idx_fai_equipment", columnList = "equipment_id"),
                @Index(name = "idx_fai_status", columnList = "status"),
                @Index(name = "idx_fai_create_time", columnList = "create_time")
        })
public class FirstArticleInspection {

    /** 待签放：已开单未下结论 */
    public static final String STATUS_PENDING = "PENDING";
    /** 已放行：量差合格，签放人签字准予量产 */
    public static final String STATUS_RELEASED = "RELEASED";
    /** 已退回再量：量差超线或品质异议，退回重新测量 */
    public static final String STATUS_RETURNED = "RETURNED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 确认单号，开单后由系统生成（FA-日期-序号），唯一 */
    @Column(name = "form_no", length = 50, unique = true)
    private String formNo;

    @Column(name = "equipment_id", nullable = false)
    private Long equipmentId;

    /** 开单时机的当前换模批次记录 ID（仅作关联追溯，显示以快照为准） */
    @Column(name = "mold_batch_record_id")
    private Long moldBatchRecordId;

    /** 开单时当前模具批次号快照 */
    @Column(name = "batch_no", nullable = false, length = 100)
    private String batchNo;

    /** 开单时当前模具型号快照 */
    @Column(name = "mold_model", nullable = false, length = 100)
    private String moldModel;

    @Column(name = "standard_length", nullable = false, precision = 10, scale = 2)
    private BigDecimal standardLength;

    @Column(name = "standard_width", nullable = false, precision = 10, scale = 2)
    private BigDecimal standardWidth;

    @Column(name = "standard_height", nullable = false, precision = 10, scale = 2)
    private BigDecimal standardHeight;

    /** 公差（±mm），长宽高三个维度共用同一允差线 */
    @Column(name = "tolerance_mm", nullable = false, precision = 10, scale = 2)
    private BigDecimal tolerance;

    @Column(name = "measured_length", nullable = false, precision = 10, scale = 2)
    private BigDecimal measuredLength;

    @Column(name = "measured_width", nullable = false, precision = 10, scale = 2)
    private BigDecimal measuredWidth;

    @Column(name = "measured_height", nullable = false, precision = 10, scale = 2)
    private BigDecimal measuredHeight;

    /** 量差 = 实测 - 标准（带符号，mm），开单时计算落库 */
    @Column(name = "length_deviation", nullable = false, precision = 10, scale = 2)
    private BigDecimal lengthDeviation;

    @Column(name = "width_deviation", nullable = false, precision = 10, scale = 2)
    private BigDecimal widthDeviation;

    @Column(name = "height_deviation", nullable = false, precision = 10, scale = 2)
    private BigDecimal heightDeviation;

    /** 是否超线：任一维度 |量差| > 公差 即超线；超线单禁止放行 */
    @Column(name = "out_of_tolerance", nullable = false)
    private boolean outOfTolerance;

    /** 放行结果：PENDING 待签放 / RELEASED 已放行 / RETURNED 已退回再量 */
    @Column(name = "status", nullable = false, length = 20)
    private String status;

    /** 开单人（调度），必填 */
    @Column(name = "operator", nullable = false, length = 100)
    private String operator;

    @Column(name = "remark", length = 500)
    private String remark;

    /** 签放人，放行时必填 */
    @Column(name = "release_signer", length = 100)
    private String releaseSigner;

    /** 签放时间 */
    @Column(name = "release_time")
    private LocalDateTime releaseTime;

    /** 退回人，退回再量时必填 */
    @Column(name = "return_operator", length = 100)
    private String returnOperator;

    /** 退回原因 */
    @Column(name = "return_reason", length = 500)
    private String returnReason;

    /** 退回时间 */
    @Column(name = "return_time")
    private LocalDateTime returnTime;

    @CreationTimestamp
    @Column(name = "create_time", updatable = false)
    private LocalDateTime createTime;

    public FirstArticleInspection() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getFormNo() {
        return formNo;
    }

    public void setFormNo(String formNo) {
        this.formNo = formNo;
    }

    public Long getEquipmentId() {
        return equipmentId;
    }

    public void setEquipmentId(Long equipmentId) {
        this.equipmentId = equipmentId;
    }

    public Long getMoldBatchRecordId() {
        return moldBatchRecordId;
    }

    public void setMoldBatchRecordId(Long moldBatchRecordId) {
        this.moldBatchRecordId = moldBatchRecordId;
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

    public BigDecimal getStandardLength() {
        return standardLength;
    }

    public void setStandardLength(BigDecimal standardLength) {
        this.standardLength = standardLength;
    }

    public BigDecimal getStandardWidth() {
        return standardWidth;
    }

    public void setStandardWidth(BigDecimal standardWidth) {
        this.standardWidth = standardWidth;
    }

    public BigDecimal getStandardHeight() {
        return standardHeight;
    }

    public void setStandardHeight(BigDecimal standardHeight) {
        this.standardHeight = standardHeight;
    }

    public BigDecimal getTolerance() {
        return tolerance;
    }

    public void setTolerance(BigDecimal tolerance) {
        this.tolerance = tolerance;
    }

    public BigDecimal getMeasuredLength() {
        return measuredLength;
    }

    public void setMeasuredLength(BigDecimal measuredLength) {
        this.measuredLength = measuredLength;
    }

    public BigDecimal getMeasuredWidth() {
        return measuredWidth;
    }

    public void setMeasuredWidth(BigDecimal measuredWidth) {
        this.measuredWidth = measuredWidth;
    }

    public BigDecimal getMeasuredHeight() {
        return measuredHeight;
    }

    public void setMeasuredHeight(BigDecimal measuredHeight) {
        this.measuredHeight = measuredHeight;
    }

    public BigDecimal getLengthDeviation() {
        return lengthDeviation;
    }

    public void setLengthDeviation(BigDecimal lengthDeviation) {
        this.lengthDeviation = lengthDeviation;
    }

    public BigDecimal getWidthDeviation() {
        return widthDeviation;
    }

    public void setWidthDeviation(BigDecimal widthDeviation) {
        this.widthDeviation = widthDeviation;
    }

    public BigDecimal getHeightDeviation() {
        return heightDeviation;
    }

    public void setHeightDeviation(BigDecimal heightDeviation) {
        this.heightDeviation = heightDeviation;
    }

    public boolean isOutOfTolerance() {
        return outOfTolerance;
    }

    public void setOutOfTolerance(boolean outOfTolerance) {
        this.outOfTolerance = outOfTolerance;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
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

    public String getReleaseSigner() {
        return releaseSigner;
    }

    public void setReleaseSigner(String releaseSigner) {
        this.releaseSigner = releaseSigner;
    }

    public LocalDateTime getReleaseTime() {
        return releaseTime;
    }

    public void setReleaseTime(LocalDateTime releaseTime) {
        this.releaseTime = releaseTime;
    }

    public String getReturnOperator() {
        return returnOperator;
    }

    public void setReturnOperator(String returnOperator) {
        this.returnOperator = returnOperator;
    }

    public String getReturnReason() {
        return returnReason;
    }

    public void setReturnReason(String returnReason) {
        this.returnReason = returnReason;
    }

    public LocalDateTime getReturnTime() {
        return returnTime;
    }

    public void setReturnTime(LocalDateTime returnTime) {
        this.returnTime = returnTime;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }
}
