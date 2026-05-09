package com.yuex.mobile.customer.tool;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yuex.common.core.entity.shop.Goods;
import com.yuex.common.core.service.shop.IGoodsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class ProductQueryTool {

    private final IGoodsService goodsService;
    private volatile Long currentUserId;
    public void setCurrentUserId(Long userId) {
        this.currentUserId = userId;
    }

    @Tool(description = "按关键词搜索上架商品，返回名称、价格、主图等摘要，用于回答用户找货、比价等问题")
    public String queryProductByKeyword(String keyword) {
        if (StringUtils.isBlank(keyword)) {
            return "请提供要搜索的商品关键词。";
        }
        LambdaQueryWrapper<Goods> w = new LambdaQueryWrapper<>();
        w.eq(Goods::getIsOnSale, true)
                .eq(Goods::getDelFlag, false)
                .and(q -> q.like(Goods::getName, keyword.trim())
                        .or()
                        .like(Goods::getKeywords, keyword.trim())
                        .or()
                        .like(Goods::getBrief, keyword.trim()))
                .last("LIMIT 8");
        List<Goods> list = goodsService.list(w);
        if (list.isEmpty()) {
            return "未找到相关上架商品，可换个关键词试试。";
        }
        List<Map<String, Object>> rows = list.stream().map(this::toBrief).collect(Collectors.toList());
        return com.alibaba.fastjson2.JSON.toJSONString(rows);
    }

    @Tool(description = "根据商品主键ID查询商品详情，用于用户指定具体商品时")
    public String getProductDetailById(Long productId) {
        if (productId == null) {
            return "商品ID无效。";
        }
        Goods g = goodsService.getById(productId);
        if (g == null || Boolean.TRUE.equals(g.getDelFlag()) || !Boolean.TRUE.equals(g.getIsOnSale())) {
            return "商品不存在或已下架。";
        }
        return com.alibaba.fastjson2.JSON.toJSONString(toDetail(g));
    }

    private Map<String, Object> toBrief(Goods g) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", g.getId());
        m.put("name", g.getName());
        m.put("brief", g.getBrief());
        m.put("retailPrice", g.getRetailPrice());
        m.put("counterPrice", g.getCounterPrice());
        m.put("picUrl", g.getPicUrl());
        m.put("unit", g.getUnit());
        return m;
    }

    private Map<String, Object> toDetail(Goods g) {
        Map<String, Object> m = toBrief(g);
        m.put("gallery", g.getGallery());
        m.put("detailHtml", g.getDetail());
        return m;
    }
}
