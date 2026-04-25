package com.yuex.common.core.service.shop.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yuex.common.core.entity.shop.ColumnGoodsRelation;
import com.yuex.common.core.mapper.shop.ColumnGoodsRelationMapper;
import com.yuex.common.core.service.shop.IColumnGoodsRelationService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 栏目商品关联表 服务实现类
 *
 * @author yuex
 * @since 2020-10-10
 */
@Service
@AllArgsConstructor
public class ColumnGoodsRelationServiceImpl extends ServiceImpl<ColumnGoodsRelationMapper, ColumnGoodsRelation> implements IColumnGoodsRelationService {

    private ColumnGoodsRelationMapper columnGoodsRelationMapper;

    @Override
    public Integer getGoodsNum(Long columnId) {
        return columnGoodsRelationMapper.getGoodsNum(columnId);
    }
}
