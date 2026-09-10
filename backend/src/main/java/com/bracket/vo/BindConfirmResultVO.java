package com.bracket.vo;

import java.util.List;

/**
 * 绑定确认结果，仅包含实际绑定成功的通过项。
 */
public class BindConfirmResultVO {

    private Integer boundCount;
    private List<BracketVO> brackets;

    public BindConfirmResultVO() {
    }

    public BindConfirmResultVO(Integer boundCount, List<BracketVO> brackets) {
        this.boundCount = boundCount;
        this.brackets = brackets;
    }

    public Integer getBoundCount() {
        return boundCount;
    }

    public void setBoundCount(Integer boundCount) {
        this.boundCount = boundCount;
    }

    public List<BracketVO> getBrackets() {
        return brackets;
    }

    public void setBrackets(List<BracketVO> brackets) {
        this.brackets = brackets;
    }
}
