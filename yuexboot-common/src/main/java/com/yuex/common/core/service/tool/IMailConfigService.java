package com.yuex.common.core.service.tool;


import com.baomidou.mybatisplus.extension.service.IService;
import com.yuex.common.core.entity.tool.EmailConfig;

public interface IMailConfigService extends IService<EmailConfig> {

    boolean checkMailConfig(EmailConfig emailConfig);
}
