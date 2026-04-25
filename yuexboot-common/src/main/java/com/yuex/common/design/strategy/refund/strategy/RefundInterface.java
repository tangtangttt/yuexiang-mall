package com.yuex.common.design.strategy.refund.strategy;

import com.yuex.common.request.OrderRefundReqVO;
import com.yuex.common.response.OrderRefundResVO;

/**
 * 退款策略接口
 */
public interface RefundInterface {

    OrderRefundResVO refund(OrderRefundReqVO reqVo);

    Integer getType();
}
