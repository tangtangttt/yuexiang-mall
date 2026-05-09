package com.yuex.mobile.customer.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yuex.common.base.controller.BaseController;
import com.yuex.mobile.customer.agent.MallCustomerReactAgent;
import com.yuex.mobile.customer.agent.MallCustomerTaskManager;
import com.yuex.mobile.customer.dto.CustomerAgentResponse;
import com.yuex.mobile.customer.entity.CustomerServiceSession;
import com.yuex.mobile.customer.service.CustomerServiceSessionService;
import com.yuex.mobile.framework.security.util.MobileSecurityUtils;
import com.yuex.util.enums.ReturnCodeEnum;
import com.yuex.util.util.R;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * AI 智能客服（SSE JSON 行，与 dodo-agent 前端协议兼容）
 */
@Slf4j
@RestController
@RequestMapping("/customer")
@RequiredArgsConstructor
public class CustomerServiceController extends BaseController {

    private final MallCustomerReactAgent mallCustomerReactAgent;
    private final CustomerServiceSessionService customerServiceSessionService;
    private final MallCustomerTaskManager mallCustomerTaskManager;

    @GetMapping(value = "/chat/stream", produces = "text/event-stream;charset=UTF-8")
    public Flux<String> chatStream(
            @RequestParam(required = false) String sessionId,
            @RequestParam String question) {
        Long memberId = MobileSecurityUtils.getUserId();
        if (memberId == null) {
            return Flux.error(new IllegalStateException("请先登录"));
        }
        // 如果 sessionId 为空或不存在，始终生成新的，避免并发冲突
        if (StringUtils.isBlank(sessionId)) {
            sessionId = UUID.randomUUID().toString().replace("-", "");
        } else {
            // 检查是否有正在进行的任务，如果有则拒绝
            if (mallCustomerTaskManager.hasRunningTask(sessionId)) {
                log.warn("会话 {} 已有任务在执行，拒绝新请求", sessionId);
                return Flux.error(new IllegalStateException("该会话正在回复中，请稍后再试"));
            }
        }
        final String sid = sessionId.trim();
        log.info("AI客服 stream sessionId={}, memberId={}, q={}", sid, memberId, question);
        return Flux.just(CustomerAgentResponse.session(sid))
                .concatWith(mallCustomerReactAgent.stream(sid, question, memberId));
    }

    @GetMapping("/stop")
    public R<Map<String, Object>> stop(@RequestParam String sessionId) {
        boolean ok = mallCustomerTaskManager.stopTask(sessionId);
        Map<String, Object> m = new HashMap<>();
        m.put("success", ok);
        m.put("message", ok ? "已停止" : "当前没有进行中的回复");
        return R.success(m);
    }

    @GetMapping("/history")
    public R<Page<CustomerServiceSession>> history(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize) {
        Long memberId = MobileSecurityUtils.getUserId();
        if (memberId == null) {
            return R.error(ReturnCodeEnum.UNAUTHORIZED);
        }
        Page<CustomerServiceSession> page = customerServiceSessionService.lambdaQuery()
                .eq(CustomerServiceSession::getMemberId, memberId)
                .eq(CustomerServiceSession::getStatus, 1)
                .orderByDesc(CustomerServiceSession::getId)
                .page(new Page<>(pageNum, pageSize));
        return R.success(page);
    }
}
