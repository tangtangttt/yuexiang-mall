package com.yuex.common.core.service.shop;

import com.baomidou.mybatisplus.extension.service.IService;
import com.yuex.common.core.entity.shop.GoodsSpecification;
import com.yuex.common.core.vo.SpecificationVO;

import java.util.List;

/**
 * 商品规格表 服务类
 *
 * @author yuex
 * @since 2020-07-06
 */
public interface IGoodsSpecificationService extends IService<GoodsSpecification> {

    List<SpecificationVO> getSpecificationVOList(Long goodsId);
}
