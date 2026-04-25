package com.yuex.data.redis.constant;

/**
 * 缓存常量
 */
public class CacheConstants {

    /**
     * 缓存前缀, 统一项目缓存前缀
     */
    public static final String CACHE_PREFIX = "yuexboot:";

    /**
     * 登录用户 redis key
     */
    public static final String LOGIN_TOKEN_KEY = CACHE_PREFIX + "login_tokens:";


    public static final String SHOP_HOME_INDEX_HASH = CACHE_PREFIX + "shop_home_index_hash";
    public static final String SHOP_HOME_INDEX_HASH_EXPIRATION_FIELD = "expire_time";
    public static final String SHOP_HOME_INDEX_CACHE = CACHE_PREFIX + "home_index_cache";
    public static final String CATEGORY_INDEX_CACHE = CACHE_PREFIX + "category_index_cache";
    public static final String LOGIN_TOKEN_CACHE = CACHE_PREFIX + "login_tokens";
    public static final String CAPTCHA_CACHE = CACHE_PREFIX + "captcha_codes";
    public static final String SYS_CONFIG_CACHE = CACHE_PREFIX + "sys_configs";
    public static final String SYS_DICT_CACHE = CACHE_PREFIX + "sys_dicts";

    public static final String ORDER_SN_INCR_KEY = CACHE_PREFIX + "order_sn_incr_key";

}
