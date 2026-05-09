package com.yuex.mobile.customer.agent;

import com.alibaba.fastjson2.JSON;
import com.yuex.mobile.customer.dto.CustomerAgentResponse;
import com.yuex.mobile.customer.record.RoundMode;
import com.yuex.mobile.customer.record.RoundState;
import com.yuex.mobile.customer.service.CustomerServiceSessionService;
import com.yuex.mobile.customer.tool.CouponQueryTool;
import com.yuex.mobile.customer.tool.KnowledgeBaseTool;
import com.yuex.mobile.customer.tool.OrderQueryTool;
import com.yuex.mobile.customer.tool.ProductQueryTool;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 商城客服 ReAct 流式智能体（实现思路对齐 dodo-agent 的 WebSearchReactAgent：多轮 tool-call + SSE JSON 行）
 */
@Slf4j
@Component
public class MallCustomerReactAgent {

    private static final String AGENT_TYPE = "mall_customer";

    private final ChatModel chatModel;
    private final List<ToolCallback> tools;
    private final CustomerServiceSessionService sessionService;
    private final MallCustomerTaskManager taskManager;
    private final CustomerChatMemoryFactory memoryFactory;
    private final ProductQueryTool productQueryTool;
    private final OrderQueryTool orderQueryTool;
    private final CouponQueryTool couponQueryTool;

    private ChatClient chatClient;

    @Value("${customer.ai.max-rounds:5}")
    private int maxRounds;

    private long startTime;
    private long firstResponseTime;
    private final Set<String> usedTools = ConcurrentHashMap.newKeySet();
    private Long currentSessionRowId;
    private String currentQuestion;

    public MallCustomerReactAgent(ChatModel chatModel,
                                  ProductQueryTool productQueryTool,
                                  OrderQueryTool orderQueryTool,
                                  CouponQueryTool couponQueryTool,
                                  KnowledgeBaseTool knowledgeBaseTool,
                                  CustomerServiceSessionService sessionService,
                                  MallCustomerTaskManager taskManager,
                                  CustomerChatMemoryFactory memoryFactory) {
        this.chatModel = chatModel;
        this.productQueryTool = productQueryTool;
        this.orderQueryTool = orderQueryTool;
        this.couponQueryTool = couponQueryTool;
        this.tools = Arrays.asList(ToolCallbacks.from(productQueryTool, orderQueryTool, couponQueryTool, knowledgeBaseTool));
        this.sessionService = sessionService;
        this.taskManager = taskManager;
        this.memoryFactory = memoryFactory;
    }


    @PostConstruct
    void initChatClient() {
        ToolCallingChatOptions toolOptions = ToolCallingChatOptions.builder()
                .toolCallbacks(tools)
                .internalToolExecutionEnabled(false)
                .build();
        this.chatClient = ChatClient.builder(chatModel)
                .defaultOptions(toolOptions)
                .defaultToolCallbacks(tools)
                .build();
    }

    public Flux<String> stream(String conversationId, String question, Long memberId) {
        if (StringUtils.isBlank(question)) {
            return Flux.error(new IllegalArgumentException("问题不能为空"));
        }
        if (conversationId == null || conversationId.isBlank()) {
            return Flux.error(new IllegalArgumentException("sessionId 不能为空"));
        }
        if (memberId == null) {
            return Flux.error(new IllegalStateException("请先登录"));
        }

        // Deleted:Flux<String> busy = checkRunningTask(conversationId);
        // Deleted:if (busy != null) {
        // Deleted:    return busy;
        // Deleted:}
        log.info("【MallCustomerReactAgent】stream 开始, conversationId={}, memberId={}, question={}", conversationId, memberId, question);
        productQueryTool.setCurrentUserId(memberId);
        orderQueryTool.setCurrentUserId(memberId);
        couponQueryTool.setCurrentUserId(memberId);

        initTimers();
        usedTools.clear();
        currentQuestion = question.trim();

        Sinks.Many<String> sink = Sinks.many().unicast().onBackpressureBuffer();
        MallCustomerTaskManager.TaskInfo taskInfo = taskManager.registerTask(conversationId, sink);
        if (taskInfo == null) {
            return Flux.error(new IllegalStateException("该会话正在回复中，请稍后再试"));
        }

        var saved = sessionService.saveQuestion(conversationId, memberId, currentQuestion);
        currentSessionRowId = saved.getId();

        ChatMemory chatMemory = memoryFactory.buildFromDb(conversationId, 24);

        List<Message> messages = Collections.synchronizedList(new ArrayList<>());
        messages.add(new SystemMessage(systemPrompt()));

        loadHistory(chatMemory, conversationId, messages);

        messages.add(new UserMessage("<question>" + currentQuestion + "</question>"));

        AtomicLong roundCounter = new AtomicLong(0);
        AtomicBoolean hasSentFinal = new AtomicBoolean(false);
        StringBuilder finalAnswer = new StringBuilder();
        StringBuilder thinking = new StringBuilder();

        scheduleRound(conversationId, messages, sink, roundCounter, hasSentFinal, finalAnswer, thinking);

        return sink.asFlux()
                .doOnNext(chunk -> {
                    recordFirstResponse();
                    appendForPersistence(chunk, finalAnswer, thinking);
                })
                .doOnCancel(() -> {
                    hasSentFinal.set(true);
                    taskManager.stopTask(conversationId);
                })
                .doFinally(sig -> {
                    saveSessionResult(finalAnswer, thinking);
                    taskManager.removeTask(conversationId);
                });
    }



        private void loadHistory(ChatMemory memory, String conversationId, List<Message> messages) {
        if (memory == null) {
            return;
        }
        List<Message> history = memory.get(conversationId);
        if (history == null || history.isEmpty()) {
            return;
        }
        messages.add(new UserMessage("对话历史："));
        for (Message msg : history) {
            if (!(msg instanceof SystemMessage)) {
                messages.add(msg);
            }
        }
    }

    private void scheduleRound(String conversationId, List<Message> messages, Sinks.Many<String> sink,
                               AtomicLong roundCounter, AtomicBoolean hasSentFinal,
                               StringBuilder finalAnswer, StringBuilder thinking) {
        roundCounter.incrementAndGet();
        RoundState state = new RoundState();

        Disposable disposable = chatClient.prompt()
                .messages(messages)
                .stream()
                .chatResponse()
                .publishOn(Schedulers.boundedElastic())
                .doOnNext(chunk -> processChunk(chunk, sink, state))
                .doOnComplete(() -> finishRound(conversationId, messages, sink, state, roundCounter, hasSentFinal, finalAnswer, thinking))
                .doOnError(err -> {
                    if (!hasSentFinal.get()) {
                        hasSentFinal.set(true);
                        sink.tryEmitNext(CustomerAgentResponse.error("处理失败：" + err.getMessage()));
                        sink.tryEmitComplete();
                    }
                })
                .subscribe();

        taskManager.setDisposable(conversationId, disposable);
    }

    private void processChunk(ChatResponse chunk, Sinks.Many<String> sink, RoundState state) {
        if (chunk == null || chunk.getResult() == null || chunk.getResult().getOutput() == null) {
            return;
        }
        Generation gen = chunk.getResult();
        String text = gen.getOutput().getText();
        List<AssistantMessage.ToolCall> tc = gen.getOutput().getToolCalls();

        if (tc != null && !tc.isEmpty()) {
            state.setMode(RoundMode.TOOL_CALL);
            for (AssistantMessage.ToolCall incoming : tc) {
                mergeToolCall(state, incoming);
            }
            return;
        }
        if (text != null) {
            sink.tryEmitNext(CustomerAgentResponse.text(text));
            state.getTextBuffer().append(text);
        }
    }

    private void mergeToolCall(RoundState state, AssistantMessage.ToolCall incoming) {
        for (int i = 0; i < state.getToolCalls().size(); i++) {
            AssistantMessage.ToolCall existing = state.getToolCalls().get(i);
            if (existing.id().equals(incoming.id())) {
                String merged = Objects.toString(existing.arguments(), "") + Objects.toString(incoming.arguments(), "");
                state.getToolCalls().set(i,
                        new AssistantMessage.ToolCall(existing.id(), existing.type(), existing.name(), merged));
                return;
            }
        }
        state.getToolCalls().add(incoming);
    }

    private void finishRound(String conversationId, List<Message> messages, Sinks.Many<String> sink, RoundState state,
                             AtomicLong roundCounter, AtomicBoolean hasSentFinal,
                             StringBuilder finalAnswer, StringBuilder thinking) {
        if (state.getMode() != RoundMode.TOOL_CALL) {
            emitRecommendations(sink);
            sink.tryEmitComplete();
            hasSentFinal.set(true);
            return;
        }

        messages.add(new AssistantMessage("", Map.of(), state.getToolCalls(), List.of()));

        if (maxRounds > 0 && roundCounter.get() >= maxRounds) {
            forceFinalAnswer(conversationId, messages, sink, hasSentFinal);
            return;
        }

        executeToolCalls(sink, state.getToolCalls(), messages, hasSentFinal, () -> {
            if (!hasSentFinal.get()) {
                scheduleRound(conversationId, messages, sink, roundCounter, hasSentFinal, finalAnswer, thinking);
            }
        });
    }

    private void forceFinalAnswer(String conversationId, List<Message> messages, Sinks.Many<String> sink,
                                  AtomicBoolean hasSentFinal) {
        List<Message> next = new ArrayList<>();
        next.add(new SystemMessage(systemPrompt()));
        for (Message msg : messages) {
            if (!(msg instanceof SystemMessage)) {
                next.add(msg);
            }
        }
        next.add(new UserMessage("已达到工具调用次数上限，请基于已有信息直接回答用户，禁止再调用工具。"));
        messages.clear();
        messages.addAll(next);

        Disposable disposable = chatClient.prompt()
                .messages(messages)
                .stream()
                .chatResponse()
                .publishOn(Schedulers.boundedElastic())
                .doOnNext(chunk -> {
                    if (chunk == null || chunk.getResult() == null || chunk.getResult().getOutput() == null) {
                        return;
                    }
                    String text = chunk.getResult().getOutput().getText();
                    if (text != null && !hasSentFinal.get()) {
                        sink.tryEmitNext(CustomerAgentResponse.text(text));
                    }
                })
                .doOnComplete(() -> {
                    emitRecommendations(sink);
                    hasSentFinal.set(true);
                    sink.tryEmitComplete();
                })
                .doOnError(err -> {
                    hasSentFinal.set(true);
                    sink.tryEmitNext(CustomerAgentResponse.error(err.getMessage()));
                    sink.tryEmitComplete();
                })
                .subscribe();
        taskManager.setDisposable(conversationId, disposable);
    }

    private void executeToolCalls(Sinks.Many<String> sink, List<AssistantMessage.ToolCall> toolCalls,
                                  List<Message> messages, AtomicBoolean hasSentFinal, Runnable onAllDone) {
        AtomicInteger done = new AtomicInteger(0);
        int total = toolCalls.size();
        for (AssistantMessage.ToolCall tc : toolCalls) {
            Schedulers.boundedElastic().schedule(() -> {
                if (hasSentFinal.get()) {
                    completeTool(done, total, onAllDone);
                    return;
                }
                String toolName = tc.name();
                sink.tryEmitNext(CustomerAgentResponse.thinking("正在调用工具：" + toolName + "\n"));

                ToolCallback callback = findTool(toolName);
                if (callback == null) {
                    addErrorToolResponse(messages, tc, "工具未找到：" + toolName);
                    completeTool(done, total, onAllDone);
                    return;
                }
                try {
                    Object result = callback.call(tc.arguments());
                    ToolResponseMessage.ToolResponse tr = new ToolResponseMessage.ToolResponse(
                            tc.id(), toolName, result != null ? result.toString() : "");
                    messages.add(new ToolResponseMessage(List.of(tr)));
                    usedTools.add(toolName);
                } catch (Exception ex) {
                    log.warn("工具执行失败 {} : {}", toolName, ex.getMessage());
                    addErrorToolResponse(messages, tc, "工具执行失败：" + ex.getMessage());
                } finally {
                    completeTool(done, total, onAllDone);
                }
            });
        }
    }

    private void completeTool(AtomicInteger done, int total, Runnable onAllDone) {
        if (done.incrementAndGet() >= total) {
            onAllDone.run();
        }
    }

    private void addErrorToolResponse(List<Message> messages, AssistantMessage.ToolCall toolCall, String errMsg) {
        ToolResponseMessage.ToolResponse tr = new ToolResponseMessage.ToolResponse(
                toolCall.id(),
                toolCall.name(),
                "{\"error\":\"" + errMsg.replace("\"", "'") + "\"}"
        );
        messages.add(new ToolResponseMessage(List.of(tr)));
    }

    private ToolCallback findTool(String name) {
        return tools.stream()
                .filter(t -> t.getToolDefinition().name().equals(name))
                .findFirst()
                .orElse(null);
    }

    private void emitRecommendations(Sinks.Many<String> sink) {
        List<String> rec = List.of(
                "帮我看看最近的订单物流",
                "最近有哪些优惠券可以领？",
                "退换货规则是怎样的？"
        );
        sink.tryEmitNext(CustomerAgentResponse.recommend(JSON.toJSONString(rec)));
    }

    private void saveSessionResult(StringBuilder finalAnswer, StringBuilder thinking) {
        if (currentSessionRowId == null || finalAnswer.isEmpty()) {
            return;
        }
        try {
            long total = getTotalResponseTime();
            String toolsStr = String.join(",", usedTools);
            List<String> rec = List.of(
                    "帮我看看最近的订单物流",
                    "最近有哪些优惠券可以领？",
                    "退换货规则是怎样的？"
            );
            sessionService.updateAnswer(
                    currentSessionRowId,
                    finalAnswer.toString(),
                    thinking.toString(),
                    toolsStr,
                    JSON.toJSONString(rec),
                    firstResponseTime > 0 ? firstResponseTime : null,
                    total > 0 ? total : null
            );
        } catch (Exception e) {
            log.warn("保存客服会话失败: {}", e.getMessage());
        }
    }

    private void appendForPersistence(String chunk, StringBuilder finalAnswer, StringBuilder thinking) {
        try {
            var json = JSON.parseObject(chunk);
            String type = json.getString("type");
            if ("session".equals(type)) {
                return;
            }
            if ("text".equals(type)) {
                finalAnswer.append(json.getString("content"));
            } else if ("thinking".equals(type)) {
                thinking.append(json.getString("content"));
            }
        } catch (Exception e) {
            finalAnswer.append(chunk);
        }
    }

    private Flux<String> checkRunningTask(String conversationId) {
        if (taskManager.hasRunningTask(conversationId)) {
            return Flux.error(new IllegalStateException("该会话正在回复中，请稍后再试"));
        }
        return null;
    }

    private void initTimers() {
        startTime = System.currentTimeMillis();
        firstResponseTime = 0;
    }

    private void recordFirstResponse() {
        if (firstResponseTime == 0 && startTime > 0) {
            firstResponseTime = System.currentTimeMillis() - startTime;
        }
    }

    private long getTotalResponseTime() {
        if (startTime == 0) {
            return 0;
        }
        return System.currentTimeMillis() - startTime;
    }

    private String systemPrompt() {
        return """
                你是「悦购商城」官方智能客服，使用中文简洁回答。
                
                【重要规则】
                1. 当用户询问订单、商品、优惠券或政策时，**必须**先调用对应工具获取真实数据，禁止编造！
                2. 用户问"查看订单"、"我的订单"、"最近订单"等，必须调用 listMyRecentOrders 工具
                3. 用户问具体订单号，调用 queryOrderByOrderSn 工具
                4. 用户搜索商品，调用 queryProductByKeyword 工具
                5. 用户问优惠券，调用 listPlatformCoupons 或 listMyUnusedCoupons 工具
                6. 用户问政策（退换货、配送等），调用 searchKnowledge 工具
                
                工具说明：
                - queryProductByKeyword(keyword)：按关键词搜索商品
                - getProductDetailById(productId)：查询商品详情
                - queryOrderByOrderSn(orderSn)：根据订单号查订单
                - listMyRecentOrders(limit)：查询用户最近的订单列表
                - listPlatformCoupons()：查询平台可领取优惠券
                - listMyUnusedCoupons()：查询用户未使用的优惠券
                - searchKnowledge(query)：检索知识库（退换货、配送、支付等）
                
                回答要求：
                - 基于工具返回的真实数据回答
                - 如果工具返回空结果，如实告知用户
                - 回答末尾可适当引导用户继续提问
                - 不要泄露其他用户数据
                """;
    }

}
