package com.yuex.common.core.service.shop.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yuex.common.core.entity.shop.GoodsProduct;
import com.yuex.common.core.mapper.shop.GoodsProductMapper;
import com.yuex.common.core.service.shop.IGoodsProductService;
import com.yuex.data.redis.constant.RedisKeyEnum;
import com.yuex.data.redis.manager.StringRedisCache;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 商品货品表 服务实现类
 *
 * @author yuex
 * @since 2020-07-06
 */
@Slf4j
@Service
@AllArgsConstructor
public class GoodsProductServiceImpl extends ServiceImpl<GoodsProductMapper, GoodsProduct> implements IGoodsProductService {

    private GoodsProductMapper goodsProductMapper;
    private StringRedisCache stringRedisCache;

    /** 库存 key 默认过期时间 7 天 */
    private static final long STOCK_KEY_TTL_SECONDS = 7 * 24 * 3600L;

    @Override
    public boolean addStock(Long productId, Integer number) {
        return goodsProductMapper.addStock(productId, number);
    }

    @Override
    public List<GoodsProduct> selectProductByIds(List<Long> productIds) {
        LambdaQueryWrapper<GoodsProduct> queryWrapper = Wrappers.lambdaQuery();
        queryWrapper.in(GoodsProduct::getId, productIds);
        return list(queryWrapper);
    }

    @Override
    public boolean reduceStock(Long productId, Integer number) {
        return goodsProductMapper.reduceStock(productId, number);
    }

    @Override
    public int initRedisStock(Long productId) {
        String stockKey = RedisKeyEnum.ORDER_STOCK_KEY.getKey(productId);
        // 先检查 key 是否已存在
        String existing = stringRedisCache.getCacheString(stockKey);
        if (existing != null) {
            return Integer.parseInt(existing);
        }
        // key 不存在，从 DB 加载
        GoodsProduct product = getById(productId);
        if (product == null) {
            return 0;
        }
        int stock = product.getNumber() != null ? product.getNumber() : 0;
        // 使用 setIfAbsent 避免并发初始化覆盖
        stringRedisCache.initStockIfAbsent(stockKey, stock, STOCK_KEY_TTL_SECONDS);
        log.info("初始化商品库存到Redis: productId={}, stock={}", productId, stock);
        return stock;
    }

    /**
     * 同步所有商品库存到 Redis
     * <p>
     * 注意：此方法仅在系统启动或维护期间调用，
     * 运行期间调用可能覆盖正在预扣减的库存导致超卖。
     * 对于已有 key 使用 setIfAbsent 不覆盖，避免影响进行中的订单。
     * </p>
     */
    @Override
    public void syncAllStockToRedis() {
        List<GoodsProduct> products = list();
        for (GoodsProduct product : products) {
            String stockKey = RedisKeyEnum.ORDER_STOCK_KEY.getKey(product.getId());
            int stock = product.getNumber() != null ? product.getNumber() : 0;
            // 使用 setIfAbsent 避免覆盖已被预扣减的库存
            stringRedisCache.initStockIfAbsent(stockKey, stock, STOCK_KEY_TTL_SECONDS);
        }
        log.info("同步所有商品库存到Redis完成，共{}个商品", products.size());
    }

}
