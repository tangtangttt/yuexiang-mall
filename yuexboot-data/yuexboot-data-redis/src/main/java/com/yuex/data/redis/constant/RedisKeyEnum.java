package com.yuex.data.redis.constant;

import lombok.Getter;

/**
 * @author: yuexaqua
 * @date: 2023/8/10 22:28
 */
@Getter
public enum RedisKeyEnum {

    CAPTCHA_KEY_CACHE(CacheConstants.CACHE_PREFIX + "captcha_key:", 300),
    EMAIL_KEY_CACHE(CacheConstants.CACHE_PREFIX + "email_key:", 300),
    MOBILE_CODE_CACHE(CacheConstants.CACHE_PREFIX + "mobile_code:", 300),
    MOBILE_CODE_SEND_CACHE(CacheConstants.CACHE_PREFIX + "mobile_send_code:", 60),
    ES_SYNC_CACHE(CacheConstants.CACHE_PREFIX + "es_sync_cache", 3600),
    CART_LOCK(CacheConstants.CACHE_PREFIX + "cart_lock:", 3600),
    EMAIL_CONSUMER_MAP(CacheConstants.CACHE_PREFIX + "email_consumer_map", 60),
    ORDER_CONSUMER_MAP(CacheConstants.CACHE_PREFIX + "order_consumer_key", 60),
    UNPAID_ORDER_CONSUMER_MAP(CacheConstants.CACHE_PREFIX + "unpaid_order_consumer_key", 60),
    ORDER_RESULT_KEY(CacheConstants.CACHE_PREFIX + "order_result_key:", 60),
    ORDER_UNPAID_KEY(CacheConstants.CACHE_PREFIX + "order_unpaid_key:", 60),
    /** 同一订单号落库幂等（与 RedisLock 配合，TTL 仅作 key 过期参考，实际在事务 afterCompletion 释放锁） */
    ORDER_SUBMIT_LOCK(CacheConstants.CACHE_PREFIX + "order_submit_lock:", 600),
    /** 异步下单接口防连点（按用户维度） */
    ORDER_ASYNC_SUBMIT_THROTTLE(CacheConstants.CACHE_PREFIX + "order_async_submit_throttle:", 3),
    /** 商品库存预扣减 key, param: productId, 不设过期，由业务逻辑管理 */
    ORDER_STOCK_KEY(CacheConstants.CACHE_PREFIX + "order_stock:", 0),
    /** 用户下单分布式锁 key, param: userId, 防止同一用户并发下单 */
    ORDER_USER_LOCK(CacheConstants.CACHE_PREFIX + "order_user_lock:", 30),
    ;

    private String key;
    private Integer expireSecond;

    RedisKeyEnum(String key, Integer expireSecond) {
        this.key = key;
        this.expireSecond = expireSecond;
    }

    public String getKey(Object param) {
        return this.getKey() + param;
    }

}
