package com.yuex.common.core.service.shop;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.yuex.common.core.entity.shop.Order;
import com.yuex.common.core.vo.OrderDetailVO;
import com.yuex.common.request.OrderCommitReqVO;
import com.yuex.common.response.OrderListResVO;
import com.yuex.common.response.OrderStatusCountResVO;
import com.yuex.common.response.SubmitOrderResVO;
import com.yuex.message.core.dto.OrderDTO;
import com.yuex.util.enums.ReturnCodeEnum;

import java.io.UnsupportedEncodingException;

/**
 * 订单表 服务类
 *
 * @author yuex
 * @since 2020-08-11
 */
public interface IMobileOrderService extends IService<Order> {

    /**
     * 添加订单记录
     *
     * @param orderDTO 订单DTO
     * @return R
     */
    void submit(OrderDTO orderDTO) throws UnsupportedEncodingException;


    /**
     * 异步下单
     *
     * @param orderCommitReqVO 订单VO
     */
    SubmitOrderResVO asyncSubmit(OrderCommitReqVO orderCommitReqVO, Long userId) throws Exception;

    /**
     * 获取订单列表
     *
     * @param page     分页对象
     * @param showType 展示类型
     * @return OrderListResVO
     */
    OrderListResVO selectListPage(IPage<Order> page, Integer showType, Long userId);

    /**
     * 测试支付成功回调
     *
     * @param orderSn 订单编号
     * @return r
     */
    String searchResult(String orderSn);

    /**
     * 订单退款
     * <p>
     * 1. 检测当前订单是否可以退款；
     * 2. 更改订单状态为已退款。
     *
     * @param orderId 订单ID
     * @return void
     */
    void refund(Long orderId, Long userId);

    /**
     * 取消订单
     * <p>
     * 1. 检测当前订单是否能够取消；
     * 2. 设置订单取消状态；
     * 3. 商品货品库存恢复；
     * 4. 返还优惠券；
     *
     * @param orderId 订单ID
     * @return r
     */
    void cancel(Long orderId, Long userId);

    /**
     * 删除订单
     * <p>
     * 1. 检测当前订单是否可以删除；
     * 2. 删除订单。
     *
     * @param orderId 订单ID
     * @param userId  当前用户ID
     * @return r
     */
    void delete(Long orderId, Long userId);

    /**
     * 确认订单
     * <p>
     * 1. 检测当前订单是否可以删除；
     * 2. 更改订单状态为已收货。
     *
     * @param orderId 订单ID
     * @param userId  当前用户ID
     * @return r
     */
    void confirm(Long orderId, Long userId);

    /**
     * 查询用户订单各状态数量（包含待支付订单数量、代发货订单数量、待收货订单数量、待评价订单数量）
     *
     * @return r
     */
    OrderStatusCountResVO statusCount(Long userId);

    OrderDetailVO getOrderDetailByOrderSn(String orderSn, Long userId);

    /**
     * 检查订单操作是否合法
     *
     * @param order 订单详情
     * @return 成功返回<code>MQConstants.STRING_TRUE</code>，失败返回<code>MQConstants.STRING_FALSE</code>，或者自定义消息
     */
    ReturnCodeEnum checkOrderOperator(Order order);
}
