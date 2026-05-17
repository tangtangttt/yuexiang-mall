package com.yuex.common.core.service.shop;

import com.baomidou.mybatisplus.extension.service.IService;
import com.yuex.common.core.entity.shop.ColumnGoodsRelation;

/**
 * 栏目商品关联表 服务类
 *
 * @author yuex
 * @since*/
public interface IColumnGoodsRelationService extends IService<ColumnGoodsRelation> {

    /**
     * 获取栏目配置的商品数量
     *
     * @param columnId 栏目ID
     * @return 商品数量
     */
    Integer getGoodsNum(Long columnId);
}
