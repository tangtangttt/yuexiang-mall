package com.yuex.test;

import com.yuex.common.core.service.shop.IPayService;
import com.yuex.common.design.strategy.pay.PayTypeEnum;
import com.yuex.common.request.OrderPayReqVO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;


/**
 * @author: yuexaqua
 * @date: 2024/4/30 16:18
 */
@Slf4j
@SpringBootTest
@RunWith(SpringRunner.class)
public class PayTest {

    @Resource
    private IPayService payService;

    /**
     * 易支付下单测试
     */
    @Test
    public void epayPreapreTest() {
        OrderPayReqVO orderPayReqVO = new OrderPayReqVO();
        orderPayReqVO.setOrderSn("1702220024000013");
        orderPayReqVO.setPayType(PayTypeEnum.EPAY_ALI.getType());
        orderPayReqVO.setReturnUrl("http://www.baidu.com");
        payService.prepay(orderPayReqVO);
    }
}
