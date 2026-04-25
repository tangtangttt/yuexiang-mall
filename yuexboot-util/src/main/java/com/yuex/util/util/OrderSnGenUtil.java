package com.yuex.util.util;


import com.yuex.data.redis.constant.CacheConstants;
import com.yuex.data.redis.manager.StringRedisCache;
import com.yuex.util.constant.Constants;
import com.yuex.util.exception.BusinessException;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * 订单编号生成帮助类
 */
@Component
@AllArgsConstructor
public class OrderSnGenUtil {

    private StringRedisCache stringRedisCache;

    /**
     * 返回订单编号，生成规则：秒级时间戳 + 递增id
     *
     * @return 订单编号
     */
    public String generateOrderSn() {
        long now = LocalDateTime.now().toEpochSecond(ZoneOffset.of("+8"));
        Integer orderSnIncrLimit = Constants.ORDER_SN_INCR_LIMIT;
        Long incrKey = stringRedisCache.luaIncrKey(CacheConstants.ORDER_SN_INCR_KEY, orderSnIncrLimit);
        if (incrKey > (long) orderSnIncrLimit) {
            throw new BusinessException("订单编号生成失败");
        }
        return now + String.format("%06d", incrKey);
    }

    /**
     * 返回退款订单编号，生成规则：秒级时间戳 + 递增id
     *
     * @return 退款订单编号
     */
    public String generateRefundOrderSn() {
        long now = LocalDateTime.now().toEpochSecond(ZoneOffset.of("+8"));
        Integer orderSnIncrLimit = Constants.ORDER_SN_INCR_LIMIT;
        Long incrKey = stringRedisCache.luaIncrKey(CacheConstants.ORDER_SN_INCR_KEY, orderSnIncrLimit);
        if (incrKey > (long) orderSnIncrLimit) {
            throw new BusinessException("订单编号生成失败");
        }
        return "RE" + now + String.format("%06d", incrKey);
    }
}
