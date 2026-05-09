package com.yuex.mobile.customer.tool;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yuex.common.core.entity.shop.ShopCoupon;
import com.yuex.common.core.service.shop.ShopCouponService;
import com.yuex.mobile.framework.security.util.MobileSecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class CouponQueryTool {

    private final ShopCouponService shopCouponService;
    private Long currentUserId;
    public void setCurrentUserId(Long userId) {
        this.currentUserId = userId;
    }
    @Tool(description = "查询商城当前可领取的平台优惠券列表（简要）")
    public String listPlatformCoupons() {
        Page<ShopCoupon> page = new Page<>(1, 15);
        Long uid = currentUserId != null ? currentUserId : MobileSecurityUtils.getUserId();
        var voPage = shopCouponService.fontList(page, uid);
        List<Map<String, Object>> rows = voPage.getRecords().stream().map(c -> {
            Map<String, Object> m = new HashMap<>();
            m.put("title", c.getTitle());
            m.put("discount", c.getDiscount());
            m.put("min", c.getMin());
            m.put("expireTime", c.getExpireTime());
            return m;
        }).collect(Collectors.toList());
        return com.alibaba.fastjson2.JSON.toJSONString(rows);
    }

    @Tool(description = "查询当前登录用户已领取且未使用的优惠券；未登录则提示登录")
    public String listMyUnusedCoupons() {
        Long uid = currentUserId != null ? currentUserId : MobileSecurityUtils.getUserId();
        if (uid == null) {
            return "请先登录后查询我的优惠券。";
        }
        Page<ShopCoupon> page = new Page<>(1, 15);
        var voPage = shopCouponService.myList(page, uid);
        List<Map<String, Object>> rows = voPage.getRecords().stream()
                .filter(c -> c.getUseStatus() != null && c.getUseStatus() == 0)
                .map(c -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("title", c.getTitle());
                    m.put("discount", c.getDiscount());
                    m.put("min", c.getMin());
                    m.put("expireTime", c.getExpireTime());
                    return m;
                })
                .collect(Collectors.toList());
        if (rows.isEmpty()) {
            return "暂无未使用的优惠券。";
        }
        return com.alibaba.fastjson2.JSON.toJSONString(rows);
    }
}
