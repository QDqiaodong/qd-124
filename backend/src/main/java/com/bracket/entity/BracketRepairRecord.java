package com.bracket.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * 支架返修单：解绑后的支架可标记为返修，先建返修单、后写回库结论。
 * 一个支架按返修时间倒序保留全部返修单；仅最新一张代表当前返修状态，
 * 作为批量挂接/换线改挂的返修放行依据，旧返修单只作历史追溯。
 *
 * 状态约束：
 * 1. 建单后处于「返修中」（未回库），必须写下回库结论与检验人才能再次批量挂接/改挂；
 * 2. 回库结论为「合格」时放行批量挂接与换线改挂；结论为「不合格」时一律拦截；
 * 3. 放行只认最新一张返修单：新回库成功后，此前的不合格结论不再放行，也不再作为待处理项。
 */
@Entity
@Table(name = "bracket_repair_record",
        indexes = {
                @Index(name = "idx_repair_bracket", columnList = "bracket_id"),
                @Index(name = "idx_repair_return_time", columnList = "return_time")
        })
public class BracketRepairRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "bracket_id", nullable = false)
    private Long bracketId;

    /** 返修单号，送修时登记，便于车间追溯 */
    @Column(name = "repair_no", nullable = false, length = 100)
    private String repairNo;

    /** 送修原因/故障描述 */
    @Column(name = "repair_reason", length = 500)
    private String repairReason;

    /** 送修时间（业务时间），用于区分当前返修单与历史返修单 */
    @Column(name = "repair_time", nullable = false)
    private LocalDateTime repairTime;

    /** 送修人 */
    @Column(name = "repair_operator", length = 100)
    private String repairOperator;

    /** 回库结论：true=合格（可再次挂接），false=不合格（拦截）；未回库时为空 */
    @Column(name = "return_result")
    private Boolean returnResult;

    /** 回库时间；未回库时为空 */
    @Column(name = "return_time")
    private LocalDateTime returnTime;

    /** 检验人：写回库结论时必填 */
    @Column(name = "inspector", length = 100)
    private String inspector;

    /** 回库备注 */
    @Column(name = "return_remark", length = 500)
    private String returnRemark;

    @CreationTimestamp
    @Column(name = "create_time", updatable = false)
    private LocalDateTime createTime;

    public BracketRepairRecord() {
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
}
