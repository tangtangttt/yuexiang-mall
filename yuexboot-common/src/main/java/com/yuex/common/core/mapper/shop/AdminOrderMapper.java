package com.yuex.common.core.mapper.shop;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.yuex.common.core.entity.shop.Order;
import com.yuex.common.request.OrderManagerReqVO;
import com.yuex.common.response.OrderManagerResVO;

/**
 * 类目表 Mapper 接口
 *
 * @author yuex
 * @since 2020-06-26
 */
public interface AdminOrderMapper extends BaseMapper<Order> {

    IPage<OrderManagerResVO> selectOrderListPage(IPage<Order> page, OrderManagerReqVO order);
}
