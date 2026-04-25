package com.yuex.mobile.api.controller;


import com.yuex.common.base.controller.BaseController;
import com.yuex.common.core.service.shop.IPayService;
import com.yuex.common.request.OrderPayReqVO;
import com.yuex.common.response.OrderPayResVO;
import com.yuex.mobile.framework.security.util.MobileSecurityUtils;
import com.yuex.util.util.R;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 支付接口
 */
@Slf4j
@RestController
@AllArgsConstructor
@RequestMapping("pay")
public class PayController extends BaseController {

    private IPayService payService;

    /**
     * 商城统一支付下单接口
     *
     * @param reqVO
     * @return
     */
    @PostMapping("prepay")
    public R<OrderPayResVO> prepay(@RequestBody @Validated OrderPayReqVO reqVO) {
        log.info("order prepay reqVO is {}", reqVO);
        return R.success(payService.prepay(reqVO));
    }

    /**
     * 用户从支付宝同步回跳后，主动查单并更新订单（异步通知未到时兜底）
     */
    @PostMapping("alipay/syncPaid")
    public R<Void> syncAlipayPaid(@RequestParam("orderSn") String orderSn) {
        payService.syncAlipayPaidOrder(orderSn, MobileSecurityUtils.getUserId());
        return R.success();
    }

}
