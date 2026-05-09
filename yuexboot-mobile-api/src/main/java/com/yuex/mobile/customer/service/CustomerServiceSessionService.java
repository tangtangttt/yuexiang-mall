package com.yuex.mobile.customer.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.yuex.mobile.customer.entity.CustomerServiceSession;

import java.util.List;

public interface CustomerServiceSessionService extends IService<CustomerServiceSession> {

    CustomerServiceSession saveQuestion(String sessionId, Long memberId, String question);

    List<CustomerServiceSession> findAnsweredHistory(String sessionId, int maxTurns);

    void updateAnswer(Long id, String answer, String thinking, String toolsUsed,
                      String recommendQuestions, Long firstResponseTime, Long totalResponseTime);
}
