package cn.hollis.llm.mentor;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Service;

@Service
public class GoodsService {
    @Tool(name = "goods_getNum", description = "根据商品名称或者数量")
    public String getGoodsNum(String name) {
        return "商品名称：" + name + "，数量：100";
    }

    @Tool(name = "goods_getDesc", description = "获取商品描述")
    public String getGoodsDesc(String name) {
        return "商品名称：" + name + "，描述：这是一个商品";
    }
}
