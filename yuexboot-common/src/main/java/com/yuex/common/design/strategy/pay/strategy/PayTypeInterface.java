package com.yuex.common.design.strategy.pay.strategy;

import com.yuex.common.request.OrderPayReqVO;
import com.yuex.common.response.OrderPayResVO;

/**
 * 支付策略接口
 */
public interface PayTypeInterface {

    OrderPayResVO pay(OrderPayReqVO reqVo);

    Integer getType();
}
