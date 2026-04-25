package com.yuex.mobile.api.controller.callback;

import com.yuex.common.core.entity.tool.EmailConfig;
import com.yuex.common.core.service.tool.IMailConfigService;
import com.yuex.common.core.vo.SendMailVO;
import com.yuex.common.util.MailUtil;
import com.yuex.util.util.R;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 邮件回调接口
 */
@RestController
@AllArgsConstructor
@RequestMapping("callback/email")
public class EmailController {

    private IMailConfigService mailConfigService;

    /**
     * 发送邮件
     *
     * @param subject 标题
     * @param content 内容
     * @param tos     接收人
     * @return R
     */
    @PostMapping
    public R sendEmail(String subject, String content, String tos) {
        EmailConfig emailConfig = mailConfigService.getById(1L);
        SendMailVO sendMailVO = new SendMailVO();
        sendMailVO.setSubject(subject);
        sendMailVO.setContent(content);
        sendMailVO.setTos(List.of(tos));
        MailUtil.sendMail(emailConfig, sendMailVO, false);
        return R.success();
    }
}
