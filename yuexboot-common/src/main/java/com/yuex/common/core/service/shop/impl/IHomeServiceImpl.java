package com.yuex.common.core.service.shop.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yuex.common.convert.MallConfigConvert;
import com.yuex.common.core.entity.shop.Banner;
import com.yuex.common.core.entity.shop.Diamond;
import com.yuex.common.core.entity.shop.Goods;
import com.yuex.common.core.service.shop.IBannerService;
import com.yuex.common.core.service.shop.IDiamondService;
import com.yuex.common.core.service.shop.IGoodsService;
import com.yuex.common.core.service.shop.IHomeService;
import com.yuex.common.response.HomeIndexResponseVO;
import com.yuex.common.response.MallConfigResponseVO;
import com.yuex.data.redis.constant.CacheConstants;
import com.yuex.data.redis.manager.StringRedisCache;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@AllArgsConstructor
public class IHomeServiceImpl implements IHomeService {

    private IBannerService iBannerService;
    private IGoodsService iGoodsService;
    private IDiamondService iDiamondService;
    private ThreadPoolTaskExecutor commonThreadPoolTaskExecutor;
    private StringRedisCache stringRedisCache;


    @Override
    public HomeIndexResponseVO index() {
        // 尝试从缓存获取
        String cacheKey = CacheConstants.SHOP_HOME_INDEX_CACHE;
        HomeIndexResponseVO cachedResult = stringRedisCache.getCacheObject(cacheKey, HomeIndexResponseVO.class);
        if (cachedResult != null) {
            log.debug("从缓存获取首页数据");
            return cachedResult;
        }

        // 缓存中没有，从数据库查询
        log.debug("从数据库查询首页数据");
        HomeIndexResponseVO responseVO = new HomeIndexResponseVO();
        try {
            List<CompletableFuture<Void>> list = new ArrayList<>(4);
            CompletableFuture<Void> f1 = CompletableFuture.supplyAsync(
                            () -> iBannerService.list(Wrappers.lambdaQuery(Banner.class).eq(Banner::getStatus, 0).orderByAsc(Banner::getSort)), commonThreadPoolTaskExecutor)
                    .thenAccept(responseVO::setBannerList);
            CompletableFuture<Void> f2 = CompletableFuture.supplyAsync(
                            () -> iDiamondService.list(Wrappers.lambdaQuery(Diamond.class).orderByAsc(Diamond::getSort).last("limit 10")), commonThreadPoolTaskExecutor)
                    .thenAccept(responseVO::setDiamondList);
            CompletableFuture<Void> f3 = CompletableFuture.supplyAsync(
                            () -> iGoodsService.selectHomeIndexGoods(Goods.builder().isNew(true).build()), commonThreadPoolTaskExecutor)
                    .thenAccept(responseVO::setNewGoodsList);
            CompletableFuture<Void> f4 = CompletableFuture.supplyAsync(
                            () -> iGoodsService.selectHomeIndexGoods(Goods.builder().isHot(true).build()), commonThreadPoolTaskExecutor)
                    .thenAccept(responseVO::setHotGoodsList);
            list.add(f1);
            list.add(f2);
            list.add(f3);
            list.add(f4);
            CompletableFuture.allOf(list.toArray(new CompletableFuture[0])).join();

            // 存入缓存，设置5分钟过期时间
            stringRedisCache.setCacheObject(cacheKey, responseVO, 300L, java.util.concurrent.TimeUnit.SECONDS);

            return responseVO;
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
        return null;
    }

    @Override
    public List<Goods> listGoodsPage(Page<Goods> page) {
        IPage<Goods> goodsIPage = iGoodsService.listPage(page, new Goods());
        return goodsIPage.getRecords();

    }

    @Override
    public MallConfigResponseVO mallConfig() {
        return MallConfigConvert.convertMallConfig();
    }

}
