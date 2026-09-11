package com.bracket.vo;

import java.util.List;

/**
 * 改挂确认结果：rehung 为一次性改挂成功（始终保持已绑定）的通过项，
 * conflicts 为未通过预检、仍留在源设备上的冲突项。
 */
public class RehangConfirmResultVO {

    private Integer rehungCount;
    private List<BracketVO> rehung;
    private List<BindCheckItemVO> conflicts;

    public RehangConfirmResultVO() {
    }

    public RehangConfirmResultVO(Integer rehungCount, List<BracketVO> rehung, List<BindCheckItemVO> conflicts) {
        this.rehungCount = rehungCount;
        this.rehung = rehung;
        this.conflicts = conflicts;
    }

    public Integer getRehungCount() {
        return rehungCount;
    }

    public void setRehungCount(Integer rehungCount) {
        this.rehungCount = rehungCount;
    }

    public List<BracketVO> getRehung() {
        return rehung;
    }

    public void setRehung(List<BracketVO> rehung) {
        this.rehung = rehung;
    }

    public List<BindCheckItemVO> getConflicts() {
        return conflicts;
    }

    public void setConflicts(List<BindCheckItemVO> conflicts) {
        this.conflicts = conflicts;
    }
}
