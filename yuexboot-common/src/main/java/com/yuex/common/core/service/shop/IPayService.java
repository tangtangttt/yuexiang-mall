package com.yuex.common.core.service.shop;

import com.yuex.common.request.OrderPayReqVO;
import com.yuex.common.response.OrderPayResVO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 订单表 服务类
 *
 * @author yuex
 * @since 2020-08-11
 */
public interface IPayService {

    /**
     * 统一下单支付接口，获取第三方支付平台返回的支付参数
     *
     * @param reqVO 订单VO
     * @return OrderPayResVO
     */
    OrderPayResVO prepay(OrderPayReqVO reqVO);

    String wxPayNotify(HttpServletRequest request, HttpServletResponse response);

    String aliPayNotify(HttpServletRequest request, HttpServletResponse response);

    String epayPayNotify(HttpServletRequest request, HttpServletResponse response);

    /**
     * 支付宝支付成功后，主动查单并落库（用于异步通知未到达或验签失败时的兜底）
     */
    void syncAlipayPaidOrder(String orderSn, Long userId);
}
