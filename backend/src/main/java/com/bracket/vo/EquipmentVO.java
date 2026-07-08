package com.bracket.vo;

public class EquipmentVO {

    private Long id;
    private String code;
    private String name;
    private Integer bracketCount;

    public EquipmentVO() {
    }

    public EquipmentVO(Long id, String code, String name, Integer bracketCount) {
        this.id = id;
        this.code = code;
        this.name = name;
        this.bracketCount = bracketCount;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Integer getBracketCount() {
        return bracketCount;
    }

    public void setBracketCount(Integer bracketCount) {
        this.bracketCount = bracketCount;
    }
}
