package com.yuex.common.core.mapper.shop;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yuex.common.core.entity.shop.ColumnGoodsRelation;

/**
 * 栏目商品关联表 Mapper 接口
 *
 * @author yuex
 * @since*/
public interface ColumnGoodsRelationMapper extends BaseMapper<ColumnGoodsRelation> {

    Integer getGoodsNum(Long columnId);
}
