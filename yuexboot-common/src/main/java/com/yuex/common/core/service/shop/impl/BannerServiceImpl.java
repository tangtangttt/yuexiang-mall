package com.yuex.common.core.service.shop.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yuex.common.core.entity.shop.Banner;
import com.yuex.common.core.mapper.shop.BannerMapper;
import com.yuex.common.core.service.shop.IBannerService;
import com.yuex.data.redis.constant.CacheConstants;
import com.yuex.data.redis.manager.StringRedisCache;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Set;

@Slf4j
@Service
@AllArgsConstructor
public class BannerServiceImpl extends ServiceImpl<BannerMapper, Banner> implements IBannerService {

    private BannerMapper bannerMapper;
    private StringRedisCache stringRedisCache;

    @Override
    public List<Banner> list(Banner banner) {
        return bannerMapper.selectBannerList(banner);
    }

    @Override
    public IPage<Banner> listPage(Page<Banner> page, Banner banner) {
        // 构建缓存key
        String cacheKey = "listPage_" + page.getSize() + ":" + page.getCurrent();
        if (banner != null) {
            if (StringUtils.hasText(banner.getTitle())) {
                cacheKey += "_title:" + banner.getTitle();
            }
            if (banner.getStatus() != null) {
                cacheKey += "_status:" + banner.getStatus();
            }
        }
        String fullKey = CacheConstants.CACHE_PREFIX + "bannerCache:" + cacheKey;

        // 尝试从缓存获取
        IPage<Banner> cachedResult = stringRedisCache.getCacheObject(fullKey, IPage.class);
        if (cachedResult != null) {
            log.debug("命中缓存：{}", fullKey);
            return cachedResult;
        }

        // 缓存未命中，查询数据库
        log.debug("未命中缓存，查询数据库：{}", fullKey);

        // 构建查询条件
        LambdaQueryWrapper<Banner> queryWrapper = new LambdaQueryWrapper<>();
        if (banner != null) {
            if (StringUtils.hasText(banner.getTitle())) {
                queryWrapper.like(Banner::getTitle, banner.getTitle());
            }
            if (banner.getStatus() != null) {
                queryWrapper.eq(Banner::getStatus, banner.getStatus());
            }
        }
        queryWrapper.orderByDesc(Banner::getCreateTime);

        // 执行分页查询
        IPage<Banner> result = page(page, queryWrapper);

        // 存入缓存，设置过期时间300秒（5分钟）
        if (result != null) {
            stringRedisCache.setCacheObject(fullKey, result, 300);
        }

        return result;
    }

    @Override
    public boolean saveBanner(Banner banner) {
        boolean result = save(banner);
        if (result) {
            clearBannerCache();
        }
        return result;
    }

    @Override
    public boolean updateBannerById(Banner banner) {
        boolean result = updateById(banner);
        if (result) {
            clearBannerCache();
        }
        return result;
    }

    @Override
    public Banner getBannerById(Long bannerId) {
        String cacheKey = "getBannerById_" + bannerId;
        String fullKey = CacheConstants.CACHE_PREFIX + "bannerCache:" + cacheKey;

        Banner cachedResult = stringRedisCache.getCacheObject(fullKey, Banner.class);
        if (cachedResult != null) {
            log.debug("命中缓存：{}", fullKey);
            return cachedResult;
        }

        log.debug("未命中缓存，查询数据库：{}", fullKey);
        Banner result = getById(bannerId);
        if (result != null) {
            stringRedisCache.setCacheObject(fullKey, result, 300);
        }
        return result;
    }

    @Override
    public boolean removeBannerById(List<Long> bannerIds) {
        boolean result = removeByIds(bannerIds);
        if (result) {
            clearBannerCache();
        }
        return result;
    }

    /**
     * 清除 banner 相关缓存
     */
    private void clearBannerCache() {
        try {
            Set<String> keys = stringRedisCache.scan(CacheConstants.CACHE_PREFIX + "bannerCache:*");
            if (keys != null && !keys.isEmpty()) {
                stringRedisCache.deleteObject(keys);
                log.info("清除 banner 缓存，key 数量：{}", keys.size());
            }
        } catch (Exception e) {
            log.error("清除 banner 缓存失败：{}", e.getMessage(), e);
        }
    }
}