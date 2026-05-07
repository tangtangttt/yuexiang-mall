package com.yuex.common.core.service.shop.impl;

import cn.hutool.core.bean.BeanUtil;
import com.alipay.api.AlipayApiException;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.github.binarywang.wxpay.exception.WxPayException;
import com.yuex.common.core.entity.shop.Member;
import com.yuex.common.core.entity.shop.Order;
import com.yuex.common.core.entity.shop.OrderGoods;
import com.yuex.common.core.entity.shop.ShopMemberCoupon;
import com.yuex.common.core.mapper.shop.AdminOrderMapper;
import com.yuex.common.core.service.shop.IGoodsProductService;
import com.yuex.common.core.service.shop.IMemberService;
import com.yuex.common.core.service.shop.IOrderGoodsService;
import com.yuex.common.core.service.shop.IOrderService;
import com.yuex.common.core.service.shop.ShopMemberCouponService;
import com.yuex.common.core.vo.MemberVO;
import com.yuex.common.core.vo.OrderGoodsVO;
import com.yuex.common.core.vo.OrderVO;
import com.yuex.common.design.strategy.pay.PayTypeEnum;
import com.yuex.common.design.strategy.refund.context.RefundContext;
import com.yuex.common.design.strategy.refund.strategy.RefundInterface;
import com.yuex.common.request.OrderManagerReqVO;
import com.yuex.common.request.OrderRefundReqVO;
import com.yuex.common.request.ShipRequestVO;
import com.yuex.common.response.OrderDetailResVO;
import com.yuex.common.response.OrderManagerResVO;
import com.yuex.util.enums.OrderStatusEnum;
import com.yuex.util.enums.RefundStatusEnum;
import com.yuex.util.enums.ReturnCodeEnum;
import com.yuex.util.exception.BusinessException;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;

import java.io.UnsupportedEncodingException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;
import java.util.Objects;

@Slf4j
@Service
@AllArgsConstructor
public class OrderServiceImpl extends ServiceImpl<AdminOrderMapper, Order> implements IOrderService {

    private AdminOrderMapper adminOrderMapper;
    private IOrderGoodsService iOrderGoodsService;
    private IGoodsProductService iGoodsProductService;
    private IMemberService iMemberService;
    private RefundContext refundContext;
    private ShopMemberCouponService shopMemberCouponService;
    private PlatformTransactionManager platformTransactionManager;

    @Override
    public IPage<OrderManagerResVO> listPage(IPage<Order> page, OrderManagerReqVO order) {
        IPage<OrderManagerResVO> orderManagerResVOIPage = adminOrderMapper.selectOrderListPage(page, order);
        for (OrderManagerResVO item : orderManagerResVOIPage.getRecords()) {
            item.setOrderStatusMsg(OrderStatusEnum.getDescByOrderStatus(item.getOrderStatus()));
            item.setRefundStatusMsg(RefundStatusEnum.getDescByRefundStatus(item.getRefundStatus()));
            item.setRefundTypeMsg(PayTypeEnum.getDescByPayType(item.getRefundType()));
            item.setPayTypeMsg(PayTypeEnum.getDescByPayType(item.getPayType()));
        }
        return orderManagerResVOIPage;
    }

    @Override
    public void refund(OrderRefundReqVO reqVO) throws UnsupportedEncodingException, WxPayException, AlipayApiException {
        String orderSn = reqVO.getOrderSn();
        BigDecimal refundMoney = reqVO.getRefundMoney();
        Order order = getByOrderSn(orderSn);
        if (order == null) {
            throw new BusinessException(ReturnCodeEnum.ORDER_NOT_FOUND);
        }
        if (refundMoney.compareTo(order.getActualPrice()) > 0) {
            throw new BusinessException(ReturnCodeEnum.ORDER_REFUND_MONEY_LARGE);
        }
        // 商品货品数量增加
        List<OrderGoods> orderGoodsList = iOrderGoodsService.list(new QueryWrapper<OrderGoods>()
                .eq("order_id", order.getId()));
        // 如果订单不是申请退款状态，则不能退款
        if (!Objects.equals(order.getOrderStatus(), OrderStatusEnum.STATUS_REFUND.getStatus())) {
            throw new BusinessException(ReturnCodeEnum.ORDER_CANNOT_REFUND_ERROR);
        }

        // 1. 先调用三方接口（事务外），确保资金安全：避免三方已退款但本地事务回滚导致账实不符
        boolean thirdPartySuccess = false;
        String refundFailReason = null;
        try {
            RefundInterface instance = refundContext.getInstance(order.getPayType());
            reqVO.setPayId(order.getPayId());
            reqVO.setTotalMoney(order.getActualPrice());
            instance.refund(reqVO);
            thirdPartySuccess = true;
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            refundFailReason = reqVO.getRefundReason() + " 退款失败：" + StringUtils.substring(e.getMessage(), 0, 2000);
        }

        // 2. 本地事务只更新 DB 状态
        TransactionStatus transaction = platformTransactionManager.getTransaction(new DefaultTransactionDefinition());
        try {
            int refundStatus = thirdPartySuccess ? 2 : 3;
            BigDecimal finalRefundMoney = thirdPartySuccess ? refundMoney : BigDecimal.ZERO;
            String finalReason = thirdPartySuccess ? reqVO.getRefundReason() : refundFailReason;
            Short orderStatus = thirdPartySuccess ? OrderStatusEnum.STATUS_REFUND_CONFIRM.getStatus() : OrderStatusEnum.STATUS_REFUND.getStatus();

            order.setOrderStatus(orderStatus);
            order.setOrderEndTime(LocalDateTime.now());
            order.setRefundStatus(refundStatus);
            order.setRefundAmount(finalRefundMoney);
            order.setRefundType(order.getPayType());
            order.setRefundContent(finalReason);
            order.setRefundTime(LocalDateTime.now());
            order.setUpdateTime(new Date());
            if (!updateById(order)) {
                throw new RuntimeException("订单退款状态更新失败");
            }

            if (refundStatus == 2) {
                for (OrderGoods orderGoods : orderGoodsList) {
                    if (!iGoodsProductService.addStock(orderGoods.getProductId(), orderGoods.getNumber())) {
                        throw new RuntimeException("商品货品库存增加失败");
                    }
                }
                // 退还优惠券
                if (order.getCouponPrice() != null && order.getCouponPrice().compareTo(BigDecimal.ZERO) > 0) {
                    shopMemberCouponService.lambdaUpdate()
                            .set(ShopMemberCoupon::getUseStatus, 0)
                            .set(ShopMemberCoupon::getOrderId, null)
                            .eq(ShopMemberCoupon::getOrderId, order.getId())
                            .eq(ShopMemberCoupon::getUseStatus, 1)
                            .update();
                    log.info("退款成功，退还优惠券，orderId：{}", order.getId());
                }
            }
            platformTransactionManager.commit(transaction);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            platformTransactionManager.rollback(transaction);
            throw new BusinessException(ReturnCodeEnum.ORDER_REFUND_ERROR, "退款状态更新失败，请人工核对");
        }
    }

    private Order getByOrderSn(String orderSn) {
        return this.lambdaQuery().eq(Order::getOrderSn, orderSn).one();
    }

    @Override
    public void ship(ShipRequestVO shipVO) {
        Long orderId = shipVO.getOrderId();
        String shipChannel = shipVO.getShipChannel();
        String shipSn = shipVO.getShipSn();
        Order order = getById(orderId);
        if (order == null || StringUtils.isEmpty(shipChannel) || StringUtils.isEmpty(shipSn)) {
            throw new BusinessException(ReturnCodeEnum.PARAMETER_TYPE_ERROR);
        }

        // 如果订单不是支付状态，则不能发货
        if (!Objects.equals(order.getOrderStatus(), OrderStatusEnum.STATUS_PAY.getStatus())) {
            throw new BusinessException(ReturnCodeEnum.ORDER_CANNOT_SHIP_ERROR);
        }

        // CAS 更新，防止并发重复发货
        if (!this.lambdaUpdate()
                .set(Order::getOrderStatus, OrderStatusEnum.STATUS_SHIP.getStatus())
                .set(Order::getShipSn, shipSn)
                .set(Order::getShipChannel, shipChannel)
                .set(Order::getShipTime, LocalDateTime.now())
                .set(Order::getUpdateTime, new Date())
                .eq(Order::getId, orderId)
                .eq(Order::getOrderStatus, OrderStatusEnum.STATUS_PAY.getStatus())
                .update()) {
            throw new BusinessException(ReturnCodeEnum.ORDER_CANNOT_SHIP_ERROR);
        }
    }

    @Override
    public OrderDetailResVO detail(Long orderId) {
        OrderDetailResVO orderDetailResVO = new OrderDetailResVO();
        Order order = getById(orderId);
        if (order == null) {
            throw new BusinessException(ReturnCodeEnum.ERROR);
        }
        List<OrderGoods> orderGoodsList = iOrderGoodsService.list(new QueryWrapper<OrderGoods>().eq("order_id", orderId));
        Member member = iMemberService.getById(order.getUserId());
        OrderVO orderVO = BeanUtil.copyProperties(order, OrderVO.class);
        orderVO.setOrderStatusMsg(OrderStatusEnum.getDescByOrderStatus(orderVO.getOrderStatus()));
        orderVO.setRefundStatusMsg(RefundStatusEnum.getDescByRefundStatus(orderVO.getRefundStatus()));
        orderVO.setRefundTypeMsg(PayTypeEnum.getDescByPayType(orderVO.getRefundType()));
        orderVO.setPayTypeMsg(PayTypeEnum.getDescByPayType(orderVO.getPayType()));
        orderDetailResVO.setOrder(orderVO);
        orderDetailResVO.setOrderGoods(BeanUtil.copyToList(orderGoodsList, OrderGoodsVO.class));
        orderDetailResVO.setUser(BeanUtil.copyProperties(member, MemberVO.class));
        return orderDetailResVO;
    }

}
