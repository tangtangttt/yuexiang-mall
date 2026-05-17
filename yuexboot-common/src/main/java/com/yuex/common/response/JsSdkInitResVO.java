package com.yuex.common.response;

import lombok.Data;

/**
 * @author:yuex
 * @date*/
@Data
public class JsSdkInitResVO {

    private String appId;
    private String timestamp;
    private String nonceStr;
    private String signature;
}
