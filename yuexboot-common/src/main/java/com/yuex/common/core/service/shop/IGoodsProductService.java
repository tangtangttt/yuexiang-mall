package com.yuex.common.core.service.shop;

import com.baomidou.mybatisplus.extension.service.IService;
import com.yuex.common.core.entity.shop.GoodsProduct;

import java.util.List;

/**
 * 商品货品表 服务类
 *
 * @author yuex
 * @since 2020-07-06
 */
public interface IGoodsProductService extends IService<GoodsProduct> {

    /**
     * 减少库存
     *
     * @param productId 商品货品ID
     * @param number    减少数量
     * @return boolean
     */
    boolean reduceStock(Long productId, Integer number);

    /**
     * 增加库存
     *
     * @param productId 商品货品ID
     * @param number    增加数量
     * @return boolean
     */
    boolean addStock(Long productId, Integer number);

    List<GoodsProduct> selectProductByIds(List<Long> productIds);

    /**
     * 初始化指定商品的 Redis 库存（懒加载，key不存在时从DB加载）
     *
     * @param productId 商品货品ID
     * @return 当前可用库存
     */
    int initRedisStock(Long productId);

    /**
     * 同步所有商品库存到 Redis
     */
    void syncAllStockToRedis();
}
