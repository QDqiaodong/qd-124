package com.bracket.dto;

import java.util.List;

/**
 * 换线改挂请求：把源设备上已挂的支架一次性改挂到目标封口机。
 * 与普通绑定不同，改挂全程不经过「未绑定」中间态：
 * 预检按目标机现行型号、长宽与容量规则逐项判定，
 * 确认后仅改挂通过项，冲突项仍留在源设备。
 */
public class RehangRequest {

    private Long sourceEquipmentId;
    private Long targetEquipmentId;
    private List<Long> bracketIds;

    public RehangRequest() {
    }

    public RehangRequest(Long sourceEquipmentId, Long targetEquipmentId, List<Long> bracketIds) {
        this.sourceEquipmentId = sourceEquipmentId;
        this.targetEquipmentId = targetEquipmentId;
        this.bracketIds = bracketIds;
    }

    public Long getSourceEquipmentId() {
        return sourceEquipmentId;
    }

    public void setSourceEquipmentId(Long sourceEquipmentId) {
        this.sourceEquipmentId = sourceEquipmentId;
    }

    public Long getTargetEquipmentId() {
        return targetEquipmentId;
    }

    public void setTargetEquipmentId(Long targetEquipmentId) {
        this.targetEquipmentId = targetEquipmentId;
    }

    public List<Long> getBracketIds() {
        return bracketIds;
    }

    public void setBracketIds(List<Long> bracketIds) {
        this.bracketIds = bracketIds;
    }
}
