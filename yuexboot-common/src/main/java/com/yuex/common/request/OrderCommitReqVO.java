package com.yuex.common.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;

/**
 * 订单数据VO
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class OrderCommitReqVO {

    /**
     * 订单编号
     */
    private String orderSn;

    /**
     * 用户购物车列表
     */
    private List<Long> cartIdArr;

    /**
     * 地址id
     */
    private Long addressId;

    /**
     * 用户持有的优惠券id
     */
    private Long userCouponId;

    /**
     * 备注
     */
    private String message;

    /**
     * 支付方式 1微信 2支付宝
     */
    private Integer payType;

    /**
     * 返回url
     */
    private String returnUrl;
}
