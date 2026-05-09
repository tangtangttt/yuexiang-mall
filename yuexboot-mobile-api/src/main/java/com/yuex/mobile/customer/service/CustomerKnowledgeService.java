package com.yuex.mobile.customer.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.yuex.mobile.customer.entity.CustomerKnowledge;

import java.util.List;

public interface CustomerKnowledgeService extends IService<CustomerKnowledge> {

    List<CustomerKnowledge> searchByKeyword(String keyword, int limit);
}
