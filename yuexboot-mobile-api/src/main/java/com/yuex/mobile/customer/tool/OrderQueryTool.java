package com.yuex.mobile.customer.tool;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yuex.common.core.entity.shop.Order;
import com.yuex.common.core.service.shop.IOrderService;
import com.yuex.mobile.framework.security.util.MobileSecurityUtils;
import com.yuex.util.enums.OrderStatusEnum;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderQueryTool {

    private final IOrderService orderService;
    private Long currentUserId;

    public void setCurrentUserId(Long userId) {
        log.info("【OrderQueryTool】setCurrentUserId 被调用, userId={}", userId);
        this.currentUserId = userId;
    }

    @Tool(description = "根据订单号查询订单状态、金额、收货信息、物流单号等；仅能查询当前登录用户自己的订单")
    public String queryOrderByOrderSn(String orderSn) {
        Long uid = currentUserId != null ? currentUserId : MobileSecurityUtils.getUserId();
        if (uid == null) {
            return "请先登录后再查询订单。";
        }
        if (StringUtils.isBlank(orderSn)) {
            return "请提供订单号。";
        }
        Order order = orderService.getOne(new LambdaQueryWrapper<Order>()
                .eq(Order::getOrderSn, orderSn.trim())
                .eq(Order::getUserId, uid)
                .eq(Order::getDelFlag, false));
        if (order == null) {
            return "未找到该订单，请确认订单号或是否属于当前账号。";
        }
        return com.alibaba.fastjson2.JSON.toJSONString(toOrderMap(order));
    }

    @Tool(description = "列出当前登录用户最近若干条订单摘要")
    public String listMyRecentOrders(int limit) {
        log.info("【OrderQueryTool】listMyRecentOrders 被调用, currentUserId={}, MobileSecurityUtils.getUserId={}", currentUserId, MobileSecurityUtils.getUserId());
        Long uid = currentUserId != null ? currentUserId : MobileSecurityUtils.getUserId();
        if (uid == null) {
            log.warn("【OrderQueryTool】用户ID为null，返回未登录提示");
            return "请先登录后再查询订单列表。";
        }
        log.info("【OrderQueryTool】开始查询订单, userId={}, limit={}", uid, limit);
        int n = Math.min(Math.max(limit, 1), 20);
        List<Order> list = orderService.list(new LambdaQueryWrapper<Order>()
                .eq(Order::getUserId, uid)
                .eq(Order::getDelFlag, false)
                .orderByDesc(Order::getId)
                .last("LIMIT " + n));
        if (list.isEmpty()) {
            log.info("【OrderQueryTool】用户 {} 暂无订单记录", uid);
            return "暂无订单记录。";
        }
        log.info("【OrderQueryTool】查询到 {} 条订单", list.size());
        List<Map<String, Object>> rows = list.stream().map(this::toBrief).collect(Collectors.toList());
        return com.alibaba.fastjson2.JSON.toJSONString(rows);
    }


    private Map<String, Object> toBrief(Order o) {
        Map<String, Object> m = new HashMap<>();
        m.put("orderSn", o.getOrderSn());
        m.put("orderStatus", o.getOrderStatus());
        m.put("orderStatusText", OrderStatusEnum.getDescByOrderStatus(o.getOrderStatus()));
        m.put("actualPrice", o.getActualPrice());
        m.put("createTime", o.getCreateTime());
        return m;
    }

    private Map<String, Object> toOrderMap(Order o) {
        Map<String, Object> m = toBrief(o);
        m.put("consignee", o.getConsignee());
        m.put("mobile", maskMobile(o.getMobile()));
        m.put("address", o.getAddress());
        m.put("shipChannel", o.getShipChannel());
        m.put("shipSn", o.getShipSn());
        m.put("payTime", o.getPayTime());
        m.put("shipTime", o.getShipTime());
        return m;
    }

    private static String maskMobile(String mobile) {
        if (mobile == null || mobile.length() < 7) {
            return mobile;
        }
        return mobile.substring(0, 3) + "****" + mobile.substring(mobile.length() - 4);
    }
}
