package com.yuex.common.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "sms.danmi")
public class DanmiSmsConfig {

    private String accountSid;
    private String authToken;
    private String accountId;
    private String apiUrl = "https://openapi.danmi.com/textSMS/sendSMS/V3";
    private boolean enabled = true;
}
