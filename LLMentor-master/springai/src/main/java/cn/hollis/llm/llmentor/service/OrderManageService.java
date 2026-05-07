package cn.hollis.llm.llmentor.service;

import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class OrderManageService {

    public String getOrderById(String orderId) {
        return "订单号：" + orderId;
    }

    public String refund(String orderId, String reason) {
        System.out.println("退款成功");
        return UUID.randomUUID().toString();
    }
}
