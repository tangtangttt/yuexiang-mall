package com.yuex.common.design.strategy.diamond.strategy;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yuex.common.core.entity.shop.Diamond;
import com.yuex.common.core.entity.shop.Goods;
import com.yuex.common.response.DiamondGoodsResVO;

/**
 * 金刚位跳转策略接口
 */
public interface DiamondJumpTypeInterface {

    DiamondGoodsResVO getGoods(Page<Goods> page, Diamond diamond);

    Integer getType();
}
