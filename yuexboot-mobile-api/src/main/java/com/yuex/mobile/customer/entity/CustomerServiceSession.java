package com.yuex.mobile.customer.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

/**
 * AI 客服会话一条用户提问及最终回复
 */
@Data
@TableName("customer_service_session")
public class CustomerServiceSession implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private String sessionId;

    private Long memberId;

    private String agentType;

    private String question;

    private String answer;

    private String thinking;

    private String toolsUsed;

    private String referenceData;

    private String recommendQuestions;

    private Long firstResponseTime;

    private Long totalResponseTime;

    private Integer status;

    private Date createTime;

    private Date updateTime;
}
