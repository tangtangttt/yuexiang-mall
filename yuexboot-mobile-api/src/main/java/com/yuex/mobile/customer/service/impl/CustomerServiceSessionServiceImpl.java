package com.yuex.mobile.customer.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yuex.mobile.customer.entity.CustomerServiceSession;
import com.yuex.mobile.customer.mapper.CustomerServiceSessionMapper;
import com.yuex.mobile.customer.service.CustomerServiceSessionService;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

@Service
public class CustomerServiceSessionServiceImpl extends ServiceImpl<CustomerServiceSessionMapper, CustomerServiceSession>
        implements CustomerServiceSessionService {

    @Override
    public CustomerServiceSession saveQuestion(String sessionId, Long memberId, String question) {
        CustomerServiceSession row = new CustomerServiceSession();
        row.setSessionId(sessionId);
        row.setMemberId(memberId);
        row.setQuestion(question);
        row.setAgentType("mall_customer_service");
        row.setStatus(1);
        save(row);
        return row;
    }

    @Override
    public List<CustomerServiceSession> findAnsweredHistory(String sessionId, int maxTurns) {
        if (maxTurns <= 0) {
            return Collections.emptyList();
        }
        List<CustomerServiceSession> list = lambdaQuery()
                .eq(CustomerServiceSession::getSessionId, sessionId)
                .eq(CustomerServiceSession::getStatus, 1)
                .isNotNull(CustomerServiceSession::getAnswer)
                .orderByDesc(CustomerServiceSession::getId)
                .last("LIMIT " + maxTurns)
                .list();
        Collections.reverse(list);
        return list;
    }

    @Override
    public void updateAnswer(Long id, String answer, String thinking, String toolsUsed,
                             String recommendQuestions, Long firstResponseTime, Long totalResponseTime) {
        if (id == null) {
            return;
        }
        lambdaUpdate()
                .eq(CustomerServiceSession::getId, id)
                .set(CustomerServiceSession::getAnswer, answer)
                .set(CustomerServiceSession::getThinking, thinking)
                .set(CustomerServiceSession::getToolsUsed, toolsUsed)
                .set(CustomerServiceSession::getRecommendQuestions, recommendQuestions)
                .set(CustomerServiceSession::getFirstResponseTime, firstResponseTime)
                .set(CustomerServiceSession::getTotalResponseTime, totalResponseTime)
                .update();
    }
}
