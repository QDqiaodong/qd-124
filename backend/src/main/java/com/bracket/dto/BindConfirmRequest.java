package com.bracket.dto;

import java.util.List;

/**
 * 校验通过后的绑定确认请求，仅绑定请求中的支架。
 */
public class BindConfirmRequest {

    private Long bracketId;
    private List<Long> bracketIds;
    private Long equipmentId;

    public Long getBracketId() {
        return bracketId;
    }

    public void setBracketId(Long bracketId) {
        this.bracketId = bracketId;
    }

    public List<Long> getBracketIds() {
        return bracketIds;
    }

    public void setBracketIds(List<Long> bracketIds) {
        this.bracketIds = bracketIds;
    }

    public Long getEquipmentId() {
        return equipmentId;
    }

    public void setEquipmentId(Long equipmentId) {
        this.equipmentId = equipmentId;
    }
}
