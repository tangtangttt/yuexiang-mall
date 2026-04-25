package com.yuex.util.util;

import lombok.Getter;

@Getter
public enum SmsTemplateEnum {

    CODE_VERIFY("SMS_123456794", "验证码");

    private final String templateCode;
    private final String desc;

    SmsTemplateEnum(String templateCode, String desc) {
        this.templateCode = templateCode;
        this.desc = desc;
    }
}

