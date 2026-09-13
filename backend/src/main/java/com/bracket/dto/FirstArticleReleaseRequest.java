package com.bracket.dto;

import java.time.LocalDateTime;

/**
 * 首件确认单签放请求：量差合格的单由签放人签字放行量产。
 */
public class FirstArticleReleaseRequest {

    /** 签放人，必填 */
    private String signer;

    /** 签放时间；为空时取服务端当前时间 */
    private LocalDateTime releaseTime;

    public String getSigner() {
        return signer;
    }

    public void setSigner(String signer) {
        this.signer = signer;
    }

    public LocalDateTime getReleaseTime() {
        return releaseTime;
    }

    public void setReleaseTime(LocalDateTime releaseTime) {
        this.releaseTime = releaseTime;
    }
}
