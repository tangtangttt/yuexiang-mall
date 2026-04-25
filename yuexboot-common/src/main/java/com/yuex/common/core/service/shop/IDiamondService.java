package com.yuex.common.core.service.shop;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.yuex.common.core.entity.shop.Diamond;

/**
 * 首页金刚区配置 服务类
 *
 * @author yuex
 * @since 2020-10-10
 */
public interface IDiamondService extends IService<Diamond> {

    IPage<Diamond> listPage(Page<Diamond> page, Diamond diamond);
}
