package com.yuex.common.core.service.shop.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yuex.common.core.entity.shop.GoodsAttribute;
import com.yuex.common.core.mapper.shop.GoodsAttributeMapper;
import com.yuex.common.core.service.shop.IGoodsAttributeService;
import org.springframework.stereotype.Service;

/**
 * 商品参数表 服务实现类
 *
 * @author yuex
 * @since 2020-07-06
 */
@Service
public class GoodsAttributeServiceImpl extends ServiceImpl<GoodsAttributeMapper, GoodsAttribute> implements IGoodsAttributeService {

}
