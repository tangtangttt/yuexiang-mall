package com.yuex.mobile.customer.tool;

import com.yuex.mobile.customer.entity.CustomerKnowledge;
import com.yuex.mobile.customer.service.CustomerKnowledgeService;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class KnowledgeBaseTool {

    private final CustomerKnowledgeService knowledgeService;

    @Tool(description = "从客服知识库检索退换货、配送、支付等常见问题；输入用户问题或关键词")
    public String searchKnowledge(String query) {
        if (StringUtils.isBlank(query)) {
            return "请提供要检索的问题描述。";
        }
        List<CustomerKnowledge> hits = knowledgeService.searchByKeyword(query.trim(), 5);
        if (hits.isEmpty()) {
            return "知识库中暂无匹配条目，可建议用户联系人工客服或换一种问法。";
        }
        List<Map<String, Object>> rows = hits.stream().map(this::toRow).collect(Collectors.toList());
        return com.alibaba.fastjson2.JSON.toJSONString(rows);
    }

    private Map<String, Object> toRow(CustomerKnowledge k) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("title", k.getTitle());
        m.put("category", k.getCategory());
        m.put("content", k.getContent());
        return m;
    }
}
