package com.yuex.common.design.strategy.pay.concretestrategy;

import cn.hutool.core.lang.id.NanoId;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.yuex.common.core.entity.shop.Order;
import com.yuex.common.core.entity.shop.OrderGoods;
import com.yuex.common.core.service.shop.IGoodsService;
import com.yuex.common.core.service.shop.IOrderGoodsService;
import com.yuex.common.core.service.shop.IOrderService;
import com.yuex.common.design.strategy.pay.PayTypeEnum;
import com.yuex.common.design.strategy.pay.strategy.PayTypeInterface;
import com.yuex.common.request.OrderPayReqVO;
import com.yuex.common.response.OrderPayResVO;
import com.yuex.util.enums.OrderStatusEnum;
import com.yuex.util.enums.ReturnCodeEnum;
import com.yuex.util.exception.BusinessException;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;

/**
 * 微信JSAPI支付策略
 */
@Slf4j
@Component
@AllArgsConstructor
public class TestPayStrategy implements PayTypeInterface {
    private IOrderService orderService;
    private IOrderGoodsService iOrderGoodsService;
    private IGoodsService iGoodsService;

    @Override
    public OrderPayResVO pay(OrderPayReqVO reqVo) {
        Order order = orderService.getOne(new QueryWrapper<Order>().eq("order_sn", reqVo.getOrderSn()));
        if (order == null) {
            throw new BusinessException(ReturnCodeEnum.ORDER_NOT_EXISTS_ERROR);
        }
        // CAS 条件更新，防止重复支付导致虚拟销量重复累加
        boolean updated = orderService.lambdaUpdate()
                .set(Order::getPayId, NanoId.randomNanoId())
                .set(Order::getPayTime, LocalDateTime.now())
                .set(Order::getOrderStatus, OrderStatusEnum.STATUS_PAY.getStatus())
                .set(Order::getUpdateTime, new Date())
                .eq(Order::getId, order.getId())
                .eq(Order::getOrderStatus, OrderStatusEnum.STATUS_CREATE.getStatus())
                .update();
        if (!updated) {
            throw new BusinessException(ReturnCodeEnum.ORDER_PAY_ERROR, "订单已支付或状态异常");
        }
        updateVirtualSales(order.getId());
        return new OrderPayResVO();
    }

    private void updateVirtualSales(Long orderId) {
        try {
            List<OrderGoods> orderGoods = iOrderGoodsService.list(Wrappers.lambdaQuery(OrderGoods.class)
                    .eq(OrderGoods::getOrderId, orderId));
            for (OrderGoods orderGood : orderGoods) {
                Long goodsId = orderGood.getGoodsId();
                Integer number = orderGood.getNumber();
                iGoodsService.updateVirtualSales(goodsId, number);
            }
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
    }

    @Override
    public Integer getType() {
        return PayTypeEnum.TEST.getType();
    }
}
