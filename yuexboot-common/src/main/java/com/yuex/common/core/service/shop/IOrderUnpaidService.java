package com.yuex.common.core.service.shop;

import com.baomidou.mybatisplus.extension.service.IService;
import com.yuex.common.core.entity.shop.Order;
import com.yuex.util.enums.OrderStatusEnum;

/**
 * 订单表 服务类
 *
 * @author yuex
 * @since 2020-08-11
 */
public interface IOrderUnpaidService extends IService<Order> {
    void unpaid(String orderSn, OrderStatusEnum statusAutoCancel);
}
