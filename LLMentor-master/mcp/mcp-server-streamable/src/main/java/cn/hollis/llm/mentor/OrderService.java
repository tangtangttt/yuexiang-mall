package cn.hollis.llm.mentor;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Service;

@Service
public class OrderService {
    @Tool(name = "order_getInfo", description = "根据订单id获取详情")
    public String getOrderInfo(String id) {
        return "订单id：" + id + "，订单详情：这是一个订单详情";
    }
}
