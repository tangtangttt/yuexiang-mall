package com.yuex.common.core.service.sms;


import com.yuex.util.util.SmsTemplateEnum;

public interface ISmsService {

    boolean sendSms(String phone, SmsTemplateEnum template, String... params);
}
