package com.bracket.dto;

public class EquipmentDTO {

    private Long id;
    private String equipmentCode;
    private String equipmentName;
    private Integer bracketCount;

    public EquipmentDTO() {
    }

    public EquipmentDTO(Long id, String equipmentCode, String equipmentName, Integer bracketCount) {
        this.id = id;
        this.equipmentCode = equipmentCode;
        this.equipmentName = equipmentName;
        this.bracketCount = bracketCount;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getEquipmentCode() {
        return equipmentCode;
    }

    public void setEquipmentCode(String equipmentCode) {
        this.equipmentCode = equipmentCode;
    }

    public String getEquipmentName() {
        return equipmentName;
    }

    public void setEquipmentName(String equipmentName) {
        this.equipmentName = equipmentName;
    }

    public Integer getBracketCount() {
        return bracketCount;
    }

    public void setBracketCount(Integer bracketCount) {
        this.bracketCount = bracketCount;
    }
}
