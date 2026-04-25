package com.yuex.mobile.api.controller;


import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yuex.common.base.controller.BaseController;
import com.yuex.common.core.entity.shop.Order;
import com.yuex.common.core.service.shop.IMobileOrderService;
import com.yuex.common.core.vo.OrderDetailVO;
import com.yuex.common.request.OrderCommitReqVO;
import com.yuex.common.response.OrderListResVO;
import com.yuex.common.response.OrderStatusCountResVO;
import com.yuex.common.response.SubmitOrderResVO;
import com.yuex.mobile.framework.security.util.MobileSecurityUtils;
import com.yuex.util.enums.ReturnCodeEnum;
import com.yuex.util.util.R;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;


/**
 * 订单接口
 *
 * @author yuex
 * @since 2024/1/15
 */
@Slf4j
@RestController
@AllArgsConstructor
@RequestMapping("order")
public class OrderController extends BaseController {

    private IMobileOrderService iMobileOrderService;

    /**
     * 根据订单编号获取订单详情
     *
     * @param orderSn 订单编号
     * @return R
     */
    @GetMapping("detail/{orderSn}")
    public R<OrderDetailVO> detail(@PathVariable String orderSn) {
        return R.success(iMobileOrderService.getOrderDetailByOrderSn(orderSn));
    }

    /**
     * 根据订单转状态展示用户订单列表
     *
     * @param showType 展示类型 0全部 1待付款订单 2待发货订单 3待收货订单 4待评价订单
     * @return
     */
    @GetMapping("list")
    public R<OrderListResVO> list(@RequestParam(defaultValue = "0") Integer showType) {
        Page<Order> page = getPage();
        return R.success(iMobileOrderService.selectListPage(page, showType, MobileSecurityUtils.getUserId()));
    }

    /**
     * 订单状态统计
     *
     * @return R
     */
    @PostMapping("statusCount")
    public R<OrderStatusCountResVO> statusCount() {
        return R.success(iMobileOrderService.statusCount(MobileSecurityUtils.getUserId()));
    }

    /**
     * 下单接口
     *
     * @param orderCommitReqVO 订单参数
     * @return R
     */
    @PostMapping("submit")
    public R<SubmitOrderResVO> submit(@RequestBody OrderCommitReqVO orderCommitReqVO) throws Exception {
        return R.success(iMobileOrderService.asyncSubmit(orderCommitReqVO, MobileSecurityUtils.getUserId()));
    }

    /**
     * 下单结果查询
     *
     * @param orderSn 订单编号
     * @return R
     */
    /**
     * 下单结果查询
     *
     * @param orderSn 订单编号
     * @return R
     */
    @GetMapping("searchResult/{orderSn}")
    public R searchResult(@PathVariable String orderSn) {
        String result = iMobileOrderService.searchResult(orderSn);

        log.debug("订单查询结果: orderSn={}, result={}", orderSn, result);

        // 情况1：处理中或不存在
        if (result == null || "error".equals(result)) {
            return R.error(5001, "订单处理中，请稍后查询");
        }

        // 情况2：成功（纯字符串"success"）
        if ("success".equals(result)) {
            Map<String, Object> data = new HashMap<>();
            data.put("orderSn", orderSn);
            data.put("status", "success");
            data.put("message", "下单成功");
            return R.success(data);
        }

        // 情况3：尝试解析JSON（兼容新格式）
        try {
            Object jsonObj = JSON.parse(result);
            if (jsonObj instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> resultMap = (Map<String, Object>) jsonObj;
                Integer code = (Integer) resultMap.get("code");

                if (code != null && code == 200) {
                    // 成功，返回数据
                    return R.success(resultMap);
                } else {
                    // 失败，返回错误信息
                    String message = (String) resultMap.getOrDefault("message", "下单失败");
                    return R.error(5001, message);
                }
            }
        } catch (Exception e) {
            log.debug("不是JSON格式，继续处理");
        }

        // 情况4：其他错误
        return R.error(5001, result);
    }

    /**
     * 取消订单
     *
     * @param orderId 订单id
     * @return R
     */
    @PostMapping("cancel/{orderId}")
    public R<Boolean> cancel(@PathVariable Long orderId) {
        iMobileOrderService.cancel(orderId);
        return R.success();
    }

    /**
     * 确认提交订单
     *
     * @param orderId 订单id
     * @return R
     */
    @PostMapping("confirm/{orderId}")
    public R<Boolean> confirm(@PathVariable Long orderId) {
        iMobileOrderService.confirm(orderId);
        return R.success();
    }

    /**
     * 删除订单
     *
     * @param orderId 订单id
     * @return R
     */
    @PostMapping("delete/{orderId}")
    public R<Boolean> delete(@PathVariable Long orderId) {
        iMobileOrderService.delete(orderId);
        return R.success();
    }

}
