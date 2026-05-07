package cn.hollis.llm.mentor;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Service;

@Service
public class TradeService {
    @Tool(name = "trade_getInfo", description = "根据交易id获取详情")
    public String getTradeInfo(String id) {
        return "交易id：" + id + "，交易详情：这是一个交易详情";
    }
}
