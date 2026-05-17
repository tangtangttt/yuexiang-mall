package com.yuex.mobile.api.controller;


import com.yuex.common.base.controller.BaseController;
import com.yuex.common.core.service.shop.IMobileOrderService;
import com.yuex.mobile.framework.security.util.MobileSecurityUtils;
import com.yuex.util.util.R;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 退款接口
 *
 * @author yuex
 * @since*/
@Slf4j
@RestController
@AllArgsConstructor
@RequestMapping("order/refund")
public class OrderRefundController extends BaseController {

    private IMobileOrderService mobileOrderService;

    /**
     * 申请退款
     *
     * @param orderId 订单id
     * @return R
     */
    @PostMapping("{orderId}")
    public R<Boolean> refund(@PathVariable Long orderId) {
        Long userId = MobileSecurityUtils.getUserId();
        mobileOrderService.refund(orderId, userId);
        return R.success();
    }

}
