package com.yuex.common.core.service.tool.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yuex.common.core.entity.tool.EmailConfig;
import com.yuex.common.core.mapper.tool.MailConfigMapper;
import com.yuex.common.core.service.tool.IMailConfigService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

@Service
public class MailConfigServiceImpl extends ServiceImpl<MailConfigMapper, EmailConfig> implements IMailConfigService {

    @Override
    public boolean checkMailConfig(EmailConfig emailConfig) {
        return !StringUtils.isEmpty(emailConfig.getFromUser())
                && !StringUtils.isEmpty(emailConfig.getHost())
                && !StringUtils.isEmpty(emailConfig.getPass());
    }
}
