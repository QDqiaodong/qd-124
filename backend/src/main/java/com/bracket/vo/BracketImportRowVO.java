package com.bracket.vo;

/**
 * 批量导入逐行校验结果。
 * status: VALID-校验通过可导入；DUPLICATE-型号已存在（提示后允许继续，导入时跳过）；
 *         INVALID-必填/格式/范围/文件内重复等错误，导入时跳过，可下载失败行。
 */
public class BracketImportRowVO {

    public static final String STATUS_VALID = "VALID";
    public static final String STATUS_DUPLICATE = "DUPLICATE";
    public static final String STATUS_INVALID = "INVALID";

    /** Excel/CSV 中对应的行号，表头为第 1 行，数据从第 2 行起 */
    private Integer rowNum;
    private String name;
    private String model;
    private Double length;
    private Double width;
    private String status;
    /** 不可导入的原因（INVALID 必有；DUPLICATE 为提示信息，如"型号已存在，将跳过"） */
    private String reason;

    public BracketImportRowVO() {
    }

    public BracketImportRowVO(Integer rowNum, String name, String model, Double length, Double width,
                              String status, String reason) {
        this.rowNum = rowNum;
        this.name = name;
        this.model = model;
        this.length = length;
        this.width = width;
        this.status = status;
        this.reason = reason;
    }

    public Integer getRowNum() {
        return rowNum;
    }

    public void setRowNum(Integer rowNum) {
        this.rowNum = rowNum;
    }

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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
