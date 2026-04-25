package com.yuex.common.core.service.shop.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yuex.common.core.entity.shop.OrderGoods;
import com.yuex.common.core.mapper.shop.OrderGoodsMapper;
import com.yuex.common.core.service.shop.IOrderGoodsService;
import org.springframework.stereotype.Service;

/**
 * 订单商品表 服务实现类
 *
 * @author yuex
 * @since 2020-08-11
 */
@Service
public class OrderGoodsServiceImpl extends ServiceImpl<OrderGoodsMapper, OrderGoods> implements IOrderGoodsService {

}
