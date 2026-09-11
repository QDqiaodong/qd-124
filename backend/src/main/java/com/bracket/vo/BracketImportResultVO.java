package com.bracket.vo;

import java.util.List;

/**
 * 批量导入确认后的结果：只写入校验通过且型号不存在的行。
 * failedRows 为最终未入库的行（校验失败 + 文件内重复 + 已存在型号），
 * 前端可据此生成失败行文件下载。
 */
public class BracketImportResultVO {

    private int totalCount;
    /** 实际新增成功的行数 */
    private int successCount;
    /** 校验失败的行数（必填缺失、尺寸超范围、文件内重复型号等） */
    private int failedCount;
    /** 型号已存在被跳过的行数 */
    private int skippedCount;
    private List<BracketImportRowVO> failedRows;

    public BracketImportResultVO() {
    }

    public BracketImportResultVO(int totalCount, int successCount, int failedCount, int skippedCount,
                                 List<BracketImportRowVO> failedRows) {
        this.totalCount = totalCount;
        this.successCount = successCount;
        this.failedCount = failedCount;
        this.skippedCount = skippedCount;
        this.failedRows = failedRows;
    }

    public int getTotalCount() {
        return totalCount;
    }

    public void setTotalCount(int totalCount) {
        this.totalCount = totalCount;
    }

    public int getSuccessCount() {
        return successCount;
    }

    public void setSuccessCount(int successCount) {
        this.successCount = successCount;
    }

    public int getFailedCount() {
        return failedCount;
    }

    public void setFailedCount(int failedCount) {
        this.failedCount = failedCount;
    }

    public int getSkippedCount() {
        return skippedCount;
    }

    public void setSkippedCount(int skippedCount) {
        this.skippedCount = skippedCount;
    }

    public List<BracketImportRowVO> getFailedRows() {
        return failedRows;
    }

    public void setFailedRows(List<BracketImportRowVO> failedRows) {
        this.failedRows = failedRows;
    }
}
