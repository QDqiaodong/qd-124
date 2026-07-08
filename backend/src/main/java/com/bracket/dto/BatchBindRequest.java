package com.bracket.dto;

import java.util.List;

public class BatchBindRequest {

    private List<Long> bracketIds;
    private Long equipmentId;

    public BatchBindRequest() {
    }

    public BatchBindRequest(List<Long> bracketIds, Long equipmentId) {
        this.bracketIds = bracketIds;
        this.equipmentId = equipmentId;
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
