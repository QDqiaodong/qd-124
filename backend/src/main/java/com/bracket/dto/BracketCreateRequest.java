package com.bracket.dto;

import java.math.BigDecimal;

public class BracketCreateRequest {

    private String name;
    private String model;
    private Double length;
    private Double width;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public Double getLength() {
        return length;
    }

    public void setLength(Double length) {
        this.length = length;
    }

    public Double getWidth() {
        return width;
    }

    public void setWidth(Double width) {
        this.width = width;
    }

    public BigDecimal getLengthMm() {
        return length != null ? BigDecimal.valueOf(length) : null;
    }

    public BigDecimal getWidthMm() {
        return width != null ? BigDecimal.valueOf(width) : null;
    }
}
