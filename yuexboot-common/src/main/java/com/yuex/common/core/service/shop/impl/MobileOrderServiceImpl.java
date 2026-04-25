package com.yuex.common.core.service.shop.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.date.DateUnit;
import cn.hutool.core.date.DateUtil;
import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yuex.common.config.yuexConfig;
import com.yuex.common.core.entity.shop.*;
import com.yuex.common.core.mapper.shop.OrderMapper;
import com.yuex.common.core.service.shop.*;
import com.yuex.common.core.vo.OrderDetailVO;
import com.yuex.common.core.vo.OrderGoodsVO;
import com.yuex.common.request.OrderCommitReqVO;
import com.yuex.common.response.OrderListDataResVO;
import com.yuex.common.response.OrderListResVO;
import com.yuex.common.response.OrderStatusCountResVO;
import com.yuex.common.response.SubmitOrderResVO;
import com.yuex.common.util.OrderHandleOption;
import com.yuex.common.util.OrderUtil;
import com.yuex.data.redis.constant.RedisKeyEnum;
import com.yuex.data.redis.manager.RedisLock;
import com.yuex.data.redis.manager.StringRedisCache;
import com.yuex.message.core.constant.MQConstants;
import com.yuex.message.core.dto.OrderDTO;
import com.yuex.util.constant.Constants;
import com.yuex.util.enums.OrderStatusEnum;
import com.yuex.util.enums.ReturnCodeEnum;
import com.yuex.util.exception.BusinessException;
import com.yuex.util.util.IdUtil;
import com.yuex.util.util.OrderSnGenUtil;
import com.yuex.util.util.bean.MyBeanUtil;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.io.UnsupportedEncodingException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.yuex.data.redis.constant.RedisKeyEnum.ORDER_RESULT_KEY;
import static com.yuex.util.constant.SysConstants.ORDER_SUBMIT_ERROR_MSG;

/**
 * 订单表 服务实现类
 *
 * @author yuex
 * @since 2020-08-11
 */
@Slf4j
@Service
@AllArgsConstructor
public class MobileOrderServiceImpl extends ServiceImpl<OrderMapper, Order> implements IMobileOrderService {

    private StringRedisCache stringRedisCache;
    private RedisLock redisLock;
    private IAddressService iAddressService;
    private ICartService iCartService;
    private IOrderGoodsService iOrderGoodsService;
    private IGoodsProductService iGoodsProductService;
    private IGoodsService iGoodsService;
    private OrderMapper orderMapper;
    private RabbitTemplate rabbitTemplate;
    private RabbitTemplate delayRabbitTemplate;
    private OrderSnGenUtil orderSnGenUtil;
    private ShopMemberCouponService shopMemberCouponService;
    private IOrderUnpaidService orderUnpaidService;

    @Override
    public OrderListResVO selectListPage(IPage<Order> page, Integer showType, Long userId) {
        List<Short> orderStatus = OrderUtil.orderStatus(showType);
        Order order = new Order();
        order.setUserId(userId);
        IPage<Order> orderIPage = orderMapper.selectOrderListPage(page, order, orderStatus);
        List<Order> orderList = orderIPage.getRecords();
        List<Long> idList = orderList.stream().map(Order::getId).collect(Collectors.toList());
        Map<Long, List<OrderGoods>> orderGoodsListMap = iOrderGoodsService
                .list(Wrappers.lambdaQuery(OrderGoods.class).in(CollectionUtils.isNotEmpty(idList), OrderGoods::getOrderId, idList))
                .stream().collect(Collectors.groupingBy(OrderGoods::getOrderId));
        List<OrderListDataResVO> dataList = new ArrayList<>();
        for (Order o : orderList) {
            OrderListDataResVO data = new OrderListDataResVO();
            data.setId(o.getId());
            data.setOrderSn(o.getOrderSn());
            data.setActualPrice(o.getActualPrice());
            data.setHandleOption(OrderUtil.build(o));
            data.setOrderStatusText(OrderUtil.orderStatusText(o));
            List<OrderGoods> orderGoodsList = orderGoodsListMap.get(o.getId());
            List<OrderGoodsVO> orderGoodsVOS = BeanUtil.copyToList(orderGoodsList, OrderGoodsVO.class);
            data.setGoodsList(orderGoodsVOS);
            dataList.add(data);
        }
        OrderListResVO resVO = new OrderListResVO();
        resVO.setData(dataList);
        resVO.setPage(orderIPage.getCurrent());
        resVO.setPages(orderIPage.getPages());
        return resVO;
    }

    @Override
    public OrderStatusCountResVO statusCount(Long userId) {
        List<Order> orderList = list(new QueryWrapper<Order>().select("order_status", "comments").eq("user_id", userId));
        long unpaid = 0;
        long unship = 0;
        long unrecv = 0;
        long uncomment = 0;
        for (Order order : orderList) {
            if (OrderUtil.isCreateStatus(order)) {
                unpaid++;
            } else if (OrderUtil.isPayStatus(order)) {
                unship++;
            } else if (OrderUtil.isShipStatus(order)) {
                unrecv++;
            } else if (OrderUtil.isConfirmStatus(order) || OrderUtil.isAutoConfirmStatus(order)) {
                uncomment += order.getComments();
            }

        }
        OrderStatusCountResVO resVO = new OrderStatusCountResVO();
        resVO.setUncomment(uncomment);
        resVO.setUnrecv(unrecv);
        resVO.setUnship(unship);
        resVO.setUnpaid(unpaid);
        return resVO;
    }

    @Override
    public OrderDetailVO getOrderDetailByOrderSn(String orderSn) {
        LambdaQueryWrapper<Order> queryWrapper = Wrappers.lambdaQuery();
        queryWrapper.eq(Order::getOrderSn, orderSn);
        Order order = getOne(queryWrapper);
        if (order == null) {
            throw new BusinessException(ReturnCodeEnum.ORDER_NOT_EXISTS_ERROR);
        }
        OrderDetailVO orderDetailVO = new OrderDetailVO();
        MyBeanUtil.copyProperties(order, orderDetailVO);
        orderDetailVO.setOrderStatusText(OrderUtil.orderStatusText(order));
        orderDetailVO.setPayTypeText(OrderUtil.payTypeText(order));
        LambdaQueryWrapper<OrderGoods> queryWrapper1 = Wrappers.lambdaQuery(OrderGoods.class);
        queryWrapper1.eq(OrderGoods::getOrderId, order.getId());
        List<OrderGoods> list = iOrderGoodsService.list(queryWrapper1);
        List<OrderGoodsVO> orderGoodsVOS = BeanUtil.copyToList(list, OrderGoodsVO.class);
        orderDetailVO.setOrderGoodsVOList(orderGoodsVOS);
        return orderDetailVO;
    }

    @Override
    public SubmitOrderResVO asyncSubmit(OrderCommitReqVO orderCommitReqVO, Long userId) {
        // ===================== 分布式锁防重：同一用户同一时刻只能有一个下单请求 =====================
        String userLockKey = RedisKeyEnum.ORDER_USER_LOCK.getKey(userId);
        if (!redisLock.lock(userLockKey)) {
            throw new BusinessException(ReturnCodeEnum.ORDER_SUBMIT_ERROR, "请勿重复提交订单");
        }

        // 记录预扣减成功的商品，用于异常回滚
        List<Map.Entry<Long, Integer>> deductedEntries = new ArrayList<>();

        try {
            OrderDTO orderDTO = new OrderDTO();
            MyBeanUtil.copyProperties(orderCommitReqVO, orderDTO);
            Long addressId = orderDTO.getAddressId();
            Long userCouponId = orderDTO.getUserCouponId();
            orderDTO.setUserId(userId);
            Address address = iAddressService.getById(addressId);
            if (!Objects.equals(address.getMemberId(), userId)) {
                throw new BusinessException(ReturnCodeEnum.ORDER_ERROR_ADDRESS_ERROR);
            }

            // 防连点限流
            String throttleKey = RedisKeyEnum.ORDER_ASYNC_SUBMIT_THROTTLE.getKey(userId);
            if (!stringRedisCache.setStringIfAbsent(throttleKey, "1", 2, TimeUnit.SECONDS)) {
                throw new BusinessException(ReturnCodeEnum.ORDER_SUBMIT_ERROR, "请勿频繁提交订单");
            }

            // 获取用户订单商品，为空默认取购物车已选中商品
            List<Long> cartIdArr = orderDTO.getCartIdArr();
            List<Cart> checkedGoodsList;
            if (CollectionUtils.isEmpty(cartIdArr)) {
                checkedGoodsList = iCartService.list(new QueryWrapper<Cart>().eq("checked", true).eq("user_id", userId));
            } else {
                checkedGoodsList = iCartService.listByIds(cartIdArr);
            }
            if (CollectionUtils.isEmpty(checkedGoodsList)) {
                throw new BusinessException(ReturnCodeEnum.ORDER_ERROR_CART_EMPTY_ERROR);
            }
            validateCheckedCartsBelongToUser(checkedGoodsList, userId);

            // ===================== Redis 预扣减库存 =====================
            for (Cart checkGoods : checkedGoodsList) {
                Long productId = checkGoods.getProductId();
                int deductNum = checkGoods.getNumber();
                String stockKey = RedisKeyEnum.ORDER_STOCK_KEY.getKey(productId);

                // 尝试预扣减
                long result = stringRedisCache.deductStock(stockKey, deductNum);
                if (result == -1) {
                    // key 不存在，懒加载初始化后重试
                    iGoodsProductService.initRedisStock(productId);
                    result = stringRedisCache.deductStock(stockKey, deductNum);
                }
                if (result == -2) {
                    // 库存不足
                    // 回滚本次请求中已经预扣减的库存
                    rollbackDeductedStock(deductedEntries);
                    Goods goods = iGoodsService.getById(checkGoods.getGoodsId());
                    GoodsProduct product = iGoodsProductService.getById(productId);
                    throw new BusinessException(String.format(ReturnCodeEnum.ORDER_ERROR_STOCK_NOT_ENOUGH.getMsg(),
                            goods.getName(), StringUtils.join(product.getSpecifications(), " ")));
                }
                if (result < 0) {
                    // 初始化后仍失败（极端情况）
                    rollbackDeductedStock(deductedEntries);
                    throw new BusinessException(ReturnCodeEnum.ORDER_ERROR_STOCK_NOT_ENOUGH.getMsg());
                }
                // 预扣减成功，记录用于异常回滚
                deductedEntries.add(Map.entry(productId, deductNum));
            }

            // 商品费用
            BigDecimal checkedGoodsPrice = BigDecimal.ZERO;
            for (Cart checkGoods : checkedGoodsList) {
                checkedGoodsPrice = checkedGoodsPrice.add(checkGoods.getPrice().multiply(new BigDecimal(checkGoods.getNumber())));
            }

            // 根据订单商品总价计算运费，满足条件（例如88元）则免运费，否则需要支付运费（例如8元）；
            BigDecimal freightPrice = BigDecimal.ZERO;
            if (checkedGoodsPrice.compareTo(yuexConfig.getFreightLimit()) < 0) {
                freightPrice = yuexConfig.getFreightPrice();
            }

            // 订单费用
            BigDecimal orderTotalPrice = checkedGoodsPrice.add(freightPrice);

            // 优惠卷抵扣费用
            BigDecimal couponPrice = BigDecimal.ZERO;
            if (userCouponId != null) {
                ShopMemberCoupon memberCoupon = shopMemberCouponService.getById(userCouponId);
                if (memberCoupon == null || memberCoupon.getUserId() != Math.toIntExact(userId)) {
                    throw new BusinessException(ReturnCodeEnum.ORDER_SUBMIT_ERROR, "优惠卷错误");
                }
                if (memberCoupon.getUseStatus() != 0 || DateUtil.compare(memberCoupon.getExpireTime(), new Date()) < 0) {
                    throw new BusinessException(ReturnCodeEnum.ORDER_SUBMIT_ERROR, "优惠卷不可用");
                }
                if (memberCoupon.getMin() > orderTotalPrice.intValue()) {
                    throw new BusinessException(ReturnCodeEnum.ORDER_SUBMIT_ERROR, "优惠卷使用门槛未达到");
                }
                couponPrice = BigDecimal.valueOf(memberCoupon.getDiscount());
            }

            // 最终支付费用
            BigDecimal actualPrice = orderTotalPrice.subtract(couponPrice).max(BigDecimal.ZERO);
            String orderSn = orderSnGenUtil.generateOrderSn();
            orderDTO.setOrderSn(orderSn);

            // 异步下单 - 发送 MQ
            String uid = IdUtil.getUid();
            CorrelationData correlationData = new CorrelationData(uid);
            Map<String, Object> map = new HashMap<>();
            map.put("order", orderDTO);
            map.put("notifyUrl", yuexConfig.getMobileUrl() + "/callback/order/submit");
            try {
                Message message = MessageBuilder
                        .withBody(JSON.toJSONString(map).getBytes(Constants.UTF_ENCODING))
                        .setContentType(MessageProperties.CONTENT_TYPE_TEXT_PLAIN)
                        .setDeliveryMode(MessageDeliveryMode.PERSISTENT)
                        .build();
                rabbitTemplate.convertAndSend(MQConstants.ORDER_DIRECT_EXCHANGE, MQConstants.ORDER_DIRECT_ROUTING, message, correlationData);
            } catch (UnsupportedEncodingException e) {
                log.error(e.getMessage(), e);
            }
            SubmitOrderResVO resVO = new SubmitOrderResVO();
            resVO.setActualPrice(actualPrice);
            resVO.setOrderSn(orderSn);
            return resVO;
        } catch (BusinessException e) {
            // 业务异常时回滚已预扣减的库存
            rollbackDeductedStock(deductedEntries);
            throw e;
        } catch (Exception e) {
            // 其他异常回滚
            rollbackDeductedStock(deductedEntries);
            throw e;
        } finally {
            // 释放用户级分布式锁
            redisLock.unLock(userLockKey);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void submit(OrderDTO orderDTO) throws UnsupportedEncodingException {
        Long userId = orderDTO.getUserId();
        String orderSn = orderDTO.getOrderSn();
        String lockKey = RedisKeyEnum.ORDER_SUBMIT_LOCK.getKey(orderSn);
        if (!redisLock.lock(lockKey)) {
            throw new BusinessException(ReturnCodeEnum.ORDER_SUBMIT_ERROR, "请勿重复提交或订单正在处理中");
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                redisLock.unLock(lockKey);
            }
        });
        if (getOne(Wrappers.lambdaQuery(Order.class).eq(Order::getOrderSn, orderSn)) != null) {
            return;
        }
        Long userCouponId = orderDTO.getUserCouponId();
        // 获取用户地址
        Long addressId = orderDTO.getAddressId();
        Address checkedAddress;
        if (Objects.isNull(addressId)) {
            throw new BusinessException(ReturnCodeEnum.ORDER_ERROR_ADDRESS_ERROR);
        }
        checkedAddress = iAddressService.getById(addressId);

        // 获取用户订单商品，为空默认取购物车已选中商品
        List<Long> cartIdArr = orderDTO.getCartIdArr();
        List<Cart> checkedGoodsList;
        if (CollectionUtils.isEmpty(cartIdArr)) {
            checkedGoodsList = iCartService.list(new QueryWrapper<Cart>().eq("checked", true).eq("user_id", userId));
        } else {
            checkedGoodsList = iCartService.listByIds(cartIdArr);
        }

        if (checkedGoodsList.isEmpty()) {
            stringRedisCache.setCacheObject(ORDER_RESULT_KEY.getKey(orderSn), "购物车为空",
                    ORDER_RESULT_KEY.getExpireSecond());
            throw new BusinessException(ReturnCodeEnum.ORDER_ERROR_CART_EMPTY_ERROR);
        }
        validateCheckedCartsBelongToUser(checkedGoodsList, userId);

        // 商品货品库存数量减少（DB 层实际扣减，Redis 已在 asyncSubmit 预扣减）
        List<Long> goodsIds = checkedGoodsList.stream().map(Cart::getGoodsId).collect(Collectors.toList());
        List<GoodsProduct> goodsProducts = iGoodsProductService.list(new QueryWrapper<GoodsProduct>().in("goods_id", goodsIds));
        Map<Long, GoodsProduct> goodsIdMap = goodsProducts.stream().collect(
                Collectors.toMap(GoodsProduct::getId, goodsProduct -> goodsProduct));

        // 记录 DB 扣减成功的商品，用于异常回滚 Redis
        List<Map.Entry<Long, Integer>> dbDeductedEntries = new ArrayList<>();
        try {
            for (Cart checkGoods : checkedGoodsList) {
                Long productId = checkGoods.getProductId();
                Long goodsId = checkGoods.getGoodsId();
                GoodsProduct product = goodsIdMap.get(productId);
                if (product != null) {
                    int remainNumber = product.getNumber() - checkGoods.getNumber();
                    if (remainNumber < 0) {
                        // DB 库存不足，回滚当前请求已扣减的 Redis 预扣减库存
                        rollbackDeductedStock(dbDeductedEntries);
                        Goods goods = iGoodsService.getById(goodsId);
                        String goodsName = goods.getName();
                        String[] specifications = product.getSpecifications();
                        stringRedisCache.setCacheObject(ORDER_RESULT_KEY.getKey(orderSn),
                                String.format(ReturnCodeEnum.ORDER_ERROR_STOCK_NOT_ENOUGH.getMsg(), goodsName, StringUtils.join(specifications, " ")),
                                ORDER_RESULT_KEY.getExpireSecond());
                        throw new BusinessException(String.format(ReturnCodeEnum.ORDER_ERROR_STOCK_NOT_ENOUGH.getMsg(),
                                goodsName, StringUtils.join(specifications, " ")));
                    }
                    if (!iGoodsProductService.reduceStock(productId, checkGoods.getNumber())) {
                        rollbackDeductedStock(dbDeductedEntries);
                        stringRedisCache.setCacheObject(ORDER_RESULT_KEY.getKey(orderSn),
                                ReturnCodeEnum.ORDER_SUBMIT_ERROR.getMsg(),
                                ORDER_RESULT_KEY.getExpireSecond());
                        throw new BusinessException(ReturnCodeEnum.ORDER_SUBMIT_ERROR);
                    }
                    dbDeductedEntries.add(Map.entry(productId, checkGoods.getNumber()));
                }
            }
        } catch (BusinessException e) {
            // DB 扣减失败，额外回滚 asyncSubmit 中预扣减的所有 Redis 库存
            List<Map.Entry<Long, Integer>> allPreDeducted = checkedGoodsList.stream()
                    .map(c -> Map.entry(c.getProductId(), c.getNumber()))
                    .collect(Collectors.toList());
            rollbackDeductedStock(allPreDeducted);
            throw e;
        }

        // 商品费用
        BigDecimal checkedGoodsPrice = new BigDecimal("0.00");
        for (Cart checkGoods : checkedGoodsList) {
            checkedGoodsPrice = checkedGoodsPrice.add(checkGoods.getPrice().multiply(new BigDecimal(checkGoods.getNumber())));
        }

        // 根据订单商品总价计算运费，满足条件（例如88元）则免运费，否则需要支付运费（例如8元）；
        BigDecimal freightPrice = new BigDecimal("0.00");
        if (checkedGoodsPrice.compareTo(yuexConfig.getFreightLimit()) < 0) {
            freightPrice = yuexConfig.getFreightPrice();
        }

        // 订单费用
        BigDecimal orderTotalPrice = checkedGoodsPrice.add(freightPrice).max(BigDecimal.ZERO);

        // 优惠卷抵扣费用
        BigDecimal couponPrice = BigDecimal.ZERO;
        if (userCouponId != null) {
            ShopMemberCoupon memberCoupon = shopMemberCouponService.getById(userCouponId);
            couponPrice = BigDecimal.valueOf(memberCoupon.getDiscount());
        }

        // 最终支付费用
        BigDecimal actualPrice = orderTotalPrice.subtract(couponPrice).max(BigDecimal.ZERO);

        // 组装订单数据
        Order order = new Order();
        order.setUserId(userId);
        order.setOrderSn(orderDTO.getOrderSn());
        order.setOrderStatus(OrderStatusEnum.STATUS_CREATE.getStatus());
        order.setConsignee(checkedAddress.getName());
        order.setMobile(checkedAddress.getTel());
        order.setMessage(orderDTO.getMessage());
        String detailedAddress = checkedAddress.getProvince() + checkedAddress.getCity() + checkedAddress.getCounty() + " " + checkedAddress.getAddressDetail();
        order.setAddress(detailedAddress);
        order.setFreightPrice(freightPrice);
        order.setCouponPrice(couponPrice);
        order.setGoodsPrice(checkedGoodsPrice);
        order.setOrderPrice(orderTotalPrice);
        order.setActualPrice(actualPrice);
        order.setCreateTime(new Date());
        if (!save(order)) {
            throw new BusinessException(ReturnCodeEnum.ORDER_SUBMIT_ERROR);
        }

        Long orderId = order.getId();
        List<OrderGoods> orderGoodsList = new ArrayList<>(checkedGoodsList.size());
        // 添加订单商品表项
        for (Cart cartGoods : checkedGoodsList) {
            // 订单商品
            OrderGoods orderGoods = new OrderGoods();
            orderGoods.setOrderId(orderId);
            orderGoods.setGoodsId(cartGoods.getGoodsId());
            orderGoods.setGoodsSn(cartGoods.getGoodsSn());
            orderGoods.setProductId(cartGoods.getProductId());
            orderGoods.setGoodsName(cartGoods.getGoodsName());
            orderGoods.setPicUrl(cartGoods.getPicUrl());
            orderGoods.setPrice(cartGoods.getPrice());
            orderGoods.setNumber(cartGoods.getNumber());
            orderGoods.setSpecifications(cartGoods.getSpecifications());
            orderGoods.setCreateTime(LocalDateTime.now());
            orderGoodsList.add(orderGoods);
        }
        if (!iOrderGoodsService.saveBatch(orderGoodsList)) {
            throw new BusinessException(ReturnCodeEnum.ORDER_SUBMIT_ERROR);
        }

        // 删除购物车里面的商品信息
        if (CollectionUtils.isEmpty(cartIdArr)) {
            iCartService.remove(new QueryWrapper<Cart>().eq("user_id", userId));
        } else {
            iCartService.removeByIds(cartIdArr);
        }
        // 修改优惠卷使用状态
        if (userCouponId != null) {
            shopMemberCouponService.lambdaUpdate()
                    .set(ShopMemberCoupon::getUseStatus, 1)
                    .set(ShopMemberCoupon::getOrderId, orderId)
                    .eq(ShopMemberCoupon::getId, userCouponId)
                    .eq(ShopMemberCoupon::getUseStatus, 0)
                    .update();
        }

        // 事务提交后再发延迟关单 MQ，避免本地事务回滚后仍触发关单回调
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    Map<String, Object> map = new HashMap<>();
                    map.put("orderSn", orderDTO.getOrderSn());
                    map.put("notifyUrl", yuexConfig.getMobileUrl() + "/callback/order/unpaid");
                    Message message = MessageBuilder
                            .withBody(JSON.toJSONString(map).getBytes(Constants.UTF_ENCODING))
                            .setContentType(MessageProperties.CONTENT_TYPE_TEXT_PLAIN)
                            .setDeliveryMode(MessageDeliveryMode.PERSISTENT)
                            .build();
                    delayRabbitTemplate.convertAndSend(MQConstants.ORDER_DELAY_EXCHANGE, MQConstants.ORDER_DELAY_ROUTING, message, messagePostProcessor -> {
                        long delayTime = yuexConfig.getUnpaidOrderCancelDelayTime() * DateUnit.MINUTE.getMillis();
                        messagePostProcessor.getMessageProperties().setDelay(Math.toIntExact(delayTime));
                        return messagePostProcessor;
                    });
                } catch (UnsupportedEncodingException e) {
                    log.error("发送关单延迟消息失败 orderSn={}", orderDTO.getOrderSn(), e);
                }
            }
        });
    }

    private static void validateCheckedCartsBelongToUser(List<Cart> checkedGoodsList, Long userId) {
        int uid = Math.toIntExact(userId);
        for (Cart c : checkedGoodsList) {
            if (c.getUserId() == null || !Objects.equals(c.getUserId(), uid)) {
                throw new BusinessException(ReturnCodeEnum.ORDER_SUBMIT_ERROR, "购物车数据无效");
            }
        }
    }

    /**
     * 回滚 Redis 预扣减的库存
     *
     * @param deductedEntries 已预扣减的商品列表 (productId -> deductNum)
     */
    private void rollbackDeductedStock(List<Map.Entry<Long, Integer>> deductedEntries) {
        for (Map.Entry<Long, Integer> entry : deductedEntries) {
            try {
                String stockKey = RedisKeyEnum.ORDER_STOCK_KEY.getKey(entry.getKey());
                stringRedisCache.rollbackStock(stockKey, entry.getValue());
                log.info("回滚Redis预扣减库存: productId={}, num={}", entry.getKey(), entry.getValue());
            } catch (Exception ex) {
                log.error("回滚Redis预扣减库存失败: productId={}, num={}", entry.getKey(), entry.getValue(), ex);
            }
        }
    }

    @Override
    public String searchResult(String orderSn) {
        // 回调里存的是 JSON 字符串，getCacheObject(key) 会按 Object 反序列化成 LinkedHashMap，不能直接强转为 String
        Object value = stringRedisCache.getCacheObject(ORDER_RESULT_KEY.getKey(orderSn), Object.class);
        if (value == null) {
            return ORDER_SUBMIT_ERROR_MSG;
        }
        if (value instanceof String) {
            return (String) value;
        }
        return JSON.toJSONString(value);
    }


    @Override
    public void refund(Long orderId) {
        Order order = this.getById(orderId);
        ReturnCodeEnum returnCodeEnum = this.checkOrderOperator(order);
        if (!ReturnCodeEnum.SUCCESS.equals(returnCodeEnum)) {
            throw new BusinessException(returnCodeEnum);
        }

        OrderHandleOption handleOption = OrderUtil.build(order);
        if (!handleOption.isRefund()) {
            throw new BusinessException(ReturnCodeEnum.ORDER_CANNOT_REFUND_ERROR);
        }

        // 设置订单申请退款状态
        if (!this.lambdaUpdate()
                .set(Order::getOrderStatus, OrderStatusEnum.STATUS_REFUND.getStatus())
                .set(Order::getUpdateTime, new Date())
                .set(Order::getRefundStatus, 1)
                .eq(Order::getId, orderId)
                .update()) {
            throw new BusinessException(ReturnCodeEnum.ERROR);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long orderId) {
        Order order = getById(orderId);
        orderUnpaidService.unpaid(order.getOrderSn(), OrderStatusEnum.STATUS_CANCEL);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long orderId) {
        Order order = getById(orderId);
        ReturnCodeEnum returnCodeEnum = checkOrderOperator(order);
        if (!ReturnCodeEnum.SUCCESS.equals(returnCodeEnum)) {
            throw new BusinessException(returnCodeEnum);
        }
        // 检测是否能够取消
        OrderHandleOption handleOption = OrderUtil.build(order);
        if (!handleOption.isDelete()) {
            throw new BusinessException(ReturnCodeEnum.ORDER_CANNOT_DELETE_ERROR);
        }
        // 删除订单
        removeById(orderId);
        // 删除订单商品
        iOrderGoodsService.remove(new QueryWrapper<OrderGoods>().eq("order_id", orderId));
    }

    @Override
    public void confirm(Long orderId) {
        Order order = getById(orderId);
        ReturnCodeEnum returnCodeEnum = checkOrderOperator(order);
        if (!ReturnCodeEnum.SUCCESS.equals(returnCodeEnum)) {
            throw new BusinessException(returnCodeEnum);
        }
        // 检测是否能够取消
        OrderHandleOption handleOption = OrderUtil.build(order);
        if (!handleOption.isConfirm()) {
            throw new BusinessException(ReturnCodeEnum.ORDER_CANNOT_CONFIRM_ERROR);
        }
        // 更改订单状态为已收货
        order.setOrderStatus(OrderStatusEnum.STATUS_CONFIRM.getStatus());
        order.setConfirmTime(LocalDateTime.now());
        order.setUpdateTime(new Date());
        updateById(order);
    }

    @Override
    public ReturnCodeEnum checkOrderOperator(Order order) {
        if (Objects.isNull(order)) {
            return ReturnCodeEnum.USER_NOT_EXISTS_ERROR;
        }
        return ReturnCodeEnum.SUCCESS;
    }

}
