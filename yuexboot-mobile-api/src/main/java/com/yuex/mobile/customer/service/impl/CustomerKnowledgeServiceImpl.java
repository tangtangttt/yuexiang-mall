package com.yuex.mobile.customer.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yuex.mobile.customer.entity.CustomerKnowledge;
import com.yuex.mobile.customer.mapper.CustomerKnowledgeMapper;
import com.yuex.mobile.customer.service.CustomerKnowledgeService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CustomerKnowledgeServiceImpl extends ServiceImpl<CustomerKnowledgeMapper, CustomerKnowledge>
        implements CustomerKnowledgeService {

    @Override
    public List<CustomerKnowledge> searchByKeyword(String keyword, int limit) {
        if (StringUtils.isBlank(keyword)) {
            return List.of();
        }
        String k = keyword.trim();
        return lambdaQuery()
                .eq(CustomerKnowledge::getStatus, 1)
                .and(w -> w.like(CustomerKnowledge::getTitle, k)
                        .or()
                        .like(CustomerKnowledge::getContent, k)
                        .or()
                        .like(CustomerKnowledge::getTags, k))
                .last("LIMIT " + Math.max(1, limit))
                .list();
    }
}
