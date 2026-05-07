package com.yuex.common.core.service.shop.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yuex.common.core.entity.shop.Order;
import com.yuex.common.core.entity.shop.OrderGoods;
import com.yuex.common.core.entity.shop.ShopMemberCoupon;
import com.yuex.common.core.mapper.shop.OrderMapper;
import com.yuex.common.core.service.shop.*;
import com.yuex.common.util.OrderUtil;
import com.yuex.data.redis.constant.RedisKeyEnum;
import com.yuex.data.redis.manager.RedisLock;
import com.yuex.data.redis.manager.StringRedisCache;
import com.yuex.util.enums.OrderStatusEnum;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;

/**
 * @author: yuexaqua
 * @date: 2023/8/15 0:47
 */
@Slf4j
@Service
@AllArgsConstructor
public class OrderUnpaidServiceImpl extends ServiceImpl<OrderMapper, Order> implements IOrderUnpaidService {

    private IOrderService orderService;
    private IOrderGoodsService orderGoodsService;
    private IGoodsProductService productService;
    private ShopMemberCouponService shopMemberCouponService;
    private RedisLock redisLock;
    private StringRedisCache stringRedisCache;

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void unpaid(String orderSn, OrderStatusEnum statusAutoCancel) {
        log.info("订单编号：{}，未支付取消操作开始", orderSn);
        try {
            boolean lock = redisLock.lock(RedisKeyEnum.ORDER_UNPAID_KEY.getKey(orderSn));
            if (lock) {

                Order order = orderService.getOne(Wrappers.lambdaQuery(Order.class)
                        .eq(Order::getOrderSn, orderSn));
                // 订单状态不是刚生成不做处理
                if (!OrderUtil.isCreateStatus(order)) {
                    return;
                }
                // 使用 CAS 原子更新，避免支付回调与超时取消并发导致状态覆盖
                boolean updated = orderService.lambdaUpdate()
                        .set(Order::getOrderStatus, statusAutoCancel.getStatus())
                        .set(Order::getOrderEndTime, LocalDateTime.now())
                        .set(Order::getUpdateTime, new Date())
                        .eq(Order::getOrderSn, orderSn)
                        .eq(Order::getOrderStatus, OrderStatusEnum.STATUS_CREATE.getStatus())
                        .update();
                if (!updated) {
                    log.info("订单编号：{} CAS更新失败，订单状态已变更（可能已支付），跳过取消", orderSn);
                    return;
                }

                // 获取订单ID（CAS成功后重新获取完整订单）
                Long orderId = order.getId();

                // 商品货品数量增加（DB 回滚）
                List<OrderGoods> orderGoodsList = orderGoodsService.list(Wrappers.lambdaQuery(OrderGoods.class)
                        .eq(OrderGoods::getOrderId, orderId));
                for (OrderGoods orderGoods : orderGoodsList) {
                    Long productId = orderGoods.getProductId();
                    Integer number = orderGoods.getNumber();
                    if (!productService.addStock(productId, number)) {
                        log.info("订单编号：{} 商品货品库存增加失败", orderId);
                        throw new RuntimeException("商品货品库存增加失败");
                    }
                    // Redis 库存回滚
                    try {
                        String stockKey = RedisKeyEnum.ORDER_STOCK_KEY.getKey(productId);
                        stringRedisCache.rollbackStock(stockKey, number);
                        log.info("订单取消回滚Redis库存: orderSn={}, productId={}, num={}", orderSn, productId, number);
                    } catch (Exception ex) {
                        log.error("订单取消回滚Redis库存失败: orderSn={}, productId={}, num={}", orderSn, productId, number, ex);
                    }
                }
                // 修改优惠卷使用状态
                shopMemberCouponService.lambdaUpdate()
                        .set(ShopMemberCoupon::getUseStatus, 0)
                        .eq(ShopMemberCoupon::getOrderId, orderId)
                        .eq(ShopMemberCoupon::getUseStatus, 1)
                        .update();
            }
        } finally {
            redisLock.unLock(RedisKeyEnum.ORDER_UNPAID_KEY.getKey(orderSn));
        }
        log.info("订单编号：{}，未支付取消操作结束", orderSn);
    }

}
