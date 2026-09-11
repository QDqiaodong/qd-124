package com.bracket.vo;

import java.util.List;

/**
 * 导入文件解析 + 逐行校验的预览结果，不写库。
 * 已有型号行以 DUPLICATE 状态返回（仅提示，允许用户继续确认）。
 */
public class BracketImportPreviewVO {

    /** 数据行总数（不含表头与完全空白行） */
    private int totalCount;
    /** 必填/范围/文件内重复型号等校验通过的行数 */
    private int validCount;
    /** 型号在档案中已存在的行数（提示，导入时跳过） */
    private int duplicateCount;
    /** 校验失败的行数（必填缺失、尺寸超范围、文件内重复型号等） */
    private int invalidCount;
    private List<BracketImportRowVO> rows;

    public BracketImportPreviewVO() {
    }

    public BracketImportPreviewVO(int totalCount, int validCount, int duplicateCount, int invalidCount,
                                  List<BracketImportRowVO> rows) {
        this.totalCount = totalCount;
        this.validCount = validCount;
        this.duplicateCount = duplicateCount;
        this.invalidCount = invalidCount;
        this.rows = rows;
    }

    public int getTotalCount() {
        return totalCount;
    }

    public void setTotalCount(int totalCount) {
        this.totalCount = totalCount;
    }

    public int getValidCount() {
        return validCount;
    }

    public void setValidCount(int validCount) {
        this.validCount = validCount;
    }

    public int getDuplicateCount() {
        return duplicateCount;
    }

    public void setDuplicateCount(int duplicateCount) {
        this.duplicateCount = duplicateCount;
    }

    public int getInvalidCount() {
        return invalidCount;
    }

    public void setInvalidCount(int invalidCount) {
        this.invalidCount = invalidCount;
    }

    public List<BracketImportRowVO> getRows() {
        return rows;
    }

    public void setRows(List<BracketImportRowVO> rows) {
        this.rows = rows;
    }
}
