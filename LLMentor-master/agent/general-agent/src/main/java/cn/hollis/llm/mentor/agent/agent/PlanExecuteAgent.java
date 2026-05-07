package cn.hollis.llm.mentor.agent.agent;

import cn.hollis.llm.mentor.agent.config.ChatModelConfig;
import cn.hollis.llm.mentor.agent.prompts.PlanExecutePromptsFactory;
import cn.hollis.llm.mentor.agent.tools.SearchService;
import cn.hollis.llm.mentor.agent.tools.WeatherService;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.util.CollectionUtils;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.stream.Collectors;


@Slf4j
public class PlanExecuteAgent {

    private final ChatModel chatModel;
    private final List<ToolCallback> tools;

    // plan-execute 总轮数
    private final int maxRounds;

    // context 压缩阈值
    private final int contextCharLimit;

    // 控制工具并发调用上限
    private final Semaphore toolSemaphore;

    // 工具重试次数
    private final int maxToolRetries;

    private PlanExecutePromptsFactory planExecutePrompts;

    private ChatMemory chatMemory;

    public PlanExecuteAgent(ChatModel chatModel,
                            List<ToolCallback> tools,
                            int maxRounds,
                            int contextCharLimit,
                            int maxToolRetries,
                            PlanExecutePromptsFactory planExecutePrompts,
                            ChatMemory chatMemory) {
        this.chatModel = chatModel;
        this.tools = tools;
        this.maxRounds = maxRounds;
        this.contextCharLimit = contextCharLimit;
        this.maxToolRetries = maxToolRetries;
        this.toolSemaphore = new Semaphore(3);
        this.planExecutePrompts = planExecutePrompts;
        this.chatMemory = chatMemory;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private ChatModel chatModel;
        private List<ToolCallback> tools = new ArrayList<>();

        // 默认迭代5轮
        private int maxRounds = 5;

        // 默认context压缩阈值20000字符
        private int contextCharLimit = 50000;

        // 默认工具重试次数2次
        private int maxToolRetries = 2;

        private PlanExecutePromptsFactory planExecutePrompts;

        private ChatMemory chatMemory;

        public Builder chatMemory(ChatMemory chatMemory) {
            this.chatMemory = chatMemory;
            return this;
        }

        public Builder chatModel(ChatModel chatModel) {
            this.chatModel = chatModel;
            return this;
        }

        public Builder tools(List<ToolCallback> tools) {
            this.tools = tools;
            return this;
        }

        public Builder tools(ToolCallback... tools) {
            this.tools = Arrays.asList(tools);
            return this;
        }

        public Builder maxRounds(int maxRounds) {
            this.maxRounds = maxRounds;
            return this;
        }

        public Builder contextCharLimit(int contextCharLimit) {
            this.contextCharLimit = contextCharLimit;
            return this;
        }

        public Builder maxToolRetries(int maxToolRetries) {
            this.maxToolRetries = maxToolRetries;
            return this;
        }

        public Builder planExecutePrompts(PlanExecutePromptsFactory planExecutePrompts) {
            this.planExecutePrompts = planExecutePrompts;
            return this;
        }

        public PlanExecuteAgent build() {
            Objects.requireNonNull(chatModel, "chatModel must not be null");
            return new PlanExecuteAgent(chatModel, tools, maxRounds, contextCharLimit, maxToolRetries, planExecutePrompts, chatMemory);
        }
    }

    public String call(String question) {
        return callInternal(null, question);
    }

    public String call(String conversationId, String question) {
        return callInternal(conversationId, question);
    }

    public String callInternal(String conversationId, String question) {

        boolean useMemory = conversationId != null && chatMemory != null;

        OverAllState state = new OverAllState(conversationId, question);

        // 加载历史记忆到上下文messages中
        if (useMemory) {
            List<Message> history = chatMemory.get(conversationId);
            if (!CollectionUtils.isEmpty(history)) {
                history.forEach(state::add);
            }
        }

        // 当前用户问题
        state.add(new UserMessage(question));

        // 当前问题存入memory
        if (useMemory) {
            chatMemory.add(conversationId, new UserMessage(question));
        }

        while (maxRounds <= 0 || state.getRound() < maxRounds) {
            state.nextRound();
            log.info("===== Plan-Execute Round {} =====", state.getRound());

            // 1.生成计划
            List<PlanTask> plan = generatePlan(state);
            log.info("【Execution Plan】\n\n" + plan);
            state.add(new AssistantMessage("【Execution Plan】\n" + plan));

            if (plan.isEmpty() || plan.stream().allMatch(t -> t.id() == null)) {
                log.info("===== No execution needed, direct answer =====");
                break;
            }

            // 2.执行
            Map<String, TaskResult> results = executePlan(plan, state);

            // 3.批判
            CritiqueResult critique = critique(state);

//            state.addRound(new PlanRoundState(
//                    state.getRound(), plan, results, critique
//            ));

            if (critique.passed()) {
                log.info("===== Goal satisfied, finish =====");
                break;
            }
            log.info("===== critique Goal not satisfied, continue round =====,\n reason is {} ", critique.feedback);
            state.add(new AssistantMessage("""
                    【Critique Feedback】
                    %s
                    """.formatted(critique.feedback())));
            // 4. 压缩context
            compressIfNeeded(state);
        }
        if (state.round == maxRounds)
            log.info("===== Max rounds reached, force finish =====");

        // 5.总结输出
        return summarize(state);
    }

    private List<PlanTask> generatePlan(OverAllState state) {

        String toolDesc = renderToolDescriptions();
        BeanOutputConverter<List<PlanTask>> converter = new BeanOutputConverter<>(new ParameterizedTypeReference<>() {
        });

        Prompt prompt = new Prompt(List.of(
                new SystemMessage("""
                            当前时间是：%s。
                        
                            当前是迭代的第 %s 轮次。
                        
                            ## 可用工具说明（仅用于规划参考）
                            %s
                        
                            ## 输出format
                            %s
                        
                        """.formatted(LocalDateTime.now(ZoneId.of("Asia/Shanghai")), state.round, toolDesc, converter.getFormat())
                        + PlanExecutePromptsFactory.buildPrompts(planExecutePrompts).getPlanPrompt()),
                new UserMessage("【对话历史】\n\n" + renderMessages(state.getMessages()))
        ));

        String json = chatModel.call(prompt).getResult().getOutput().getText();

        List<PlanTask> planTasks = converter.convert(json);
        return planTasks;
    }

    private String renderToolDescriptions() {
        if (tools == null || tools.isEmpty()) {
            return "（当前无可用工具）";
        }

        StringBuilder sb = new StringBuilder();
        for (ToolCallback tool : tools) {
            sb.append("- ")
                    .append(tool.getToolDefinition().name())
                    .append(": ")
                    .append(tool.getToolDefinition().description())
                    .append("\n");
        }
        return sb.toString();
    }


    private Map<String, TaskResult> executePlan(List<PlanTask> plan, OverAllState state) {

        Map<String, TaskResult> results = new ConcurrentHashMap<>();

        // 按 order 分组：order 相同的 task 可并行
        Map<Integer, List<PlanTask>> grouped =
                plan.stream().collect(Collectors.groupingBy(PlanTask::order));

        Map<String, String> accumulatedResults = new ConcurrentHashMap<>();

        // 按 order 顺序执行（不同 order 串行）
        for (Integer order : new TreeSet<>(grouped.keySet())) {

            // 保存当前工具执行快照
            String dependencySnapshot = renderDependencySnapshot(accumulatedResults);

            List<PlanTask> tasks = grouped.get(order);

            List<CompletableFuture<Void>> futures = tasks.stream()
                    .map(task -> CompletableFuture.runAsync(() -> {

                        try {
                            // 获取执行许可
                            toolSemaphore.acquire();
                            if (task == null || StringUtils.isBlank(task.id())) {
                                return;
                            }
                            TaskResult result = executeWithRetry(task, dependencySnapshot);
                            results.put(task.id(), result);

                            if (result.success() && result.output() != null) {
                                accumulatedResults.put(task.id(), result.output());
                            }

                            state.add(new AssistantMessage("""
                                    【Completed Task Result】
                                    taskId: %s
                                    success: %s
                                    result:
                                    %s
                                    error:
                                    %s
                                    【End Task Result】
                                    """.formatted(
                                    task.id(),
                                    result.success(),
                                    result.output(),
                                    result.error()
                            )));

                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();

                            results.put(task.id(),
                                    new TaskResult(
                                            task.id(),
                                            false,
                                            null,
                                            "Task execution interrupted"
                                    ));
                        } finally {
                            // 释放许可
                            toolSemaphore.release();
                        }

                    }))
                    .toList();

            // 等待当前order组全部完成
            CompletableFuture.allOf(
                    futures.toArray(new CompletableFuture[0])
            ).join();
        }

        return results;
    }


    private TaskResult executeWithRetry(PlanTask task, String dependencySnapshot) {

        int attempt = 0;
        Throwable lastError = null;

        while (attempt < maxToolRetries) {
            attempt++;
            try {
                SimpleReactAgent agent = SimpleReactAgent.builder()
                        .chatModel(chatModel)
                        .tools(tools)
                        .maxRounds(5)
                        .systemPrompt(PlanExecutePromptsFactory.buildPrompts(planExecutePrompts).getExecutePrompt())
                        .build();

                String result = agent.call("""
                        【Available Results】
                        %s
                        
                        【Current Task】
                        %s
                        """.formatted(
                        dependencySnapshot.isBlank() ? "NONE" : dependencySnapshot,
                        task.instruction
                ));

                return new TaskResult(task.id(), true, result, null);
            } catch (Exception e) {
                lastError = e;
                log.warn("Task {} failed attempt {}/{}", task.id(), attempt, maxToolRetries, e);
            }
        }

        return new TaskResult(
                task.id(),
                false,
                null,
                lastError == null ? "unknown error" : lastError.getMessage()
        );
    }

    private String renderDependencySnapshot(Map<String, String> results) {

        if (results.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();

        results.forEach((taskId, output) -> {
            sb.append("- taskId: ")
                    .append(taskId)
                    .append("\n")
                    .append("  output:\n")
                    .append(output)
                    .append("\n\n");
        });

        return sb.toString();
    }


    private CritiqueResult critique(OverAllState state) {

        BeanOutputConverter<CritiqueResult> converter = new BeanOutputConverter<>(new ParameterizedTypeReference<>() {
        });

        Prompt prompt = new Prompt(List.of(
                new SystemMessage(PlanExecutePromptsFactory.buildPrompts(planExecutePrompts).getCritiquePrompt()),
                new UserMessage(renderMessages(state.getMessages()))
        ));
        String raw = chatModel.call(prompt).getResult().getOutput().getText();

        return converter.convert(raw);
    }

    private void compressIfNeeded(OverAllState state) {

        if (state.currentChars() < contextCharLimit) {
            return;
        }

        log.warn("===== Context too large, compressing ,size is {} =====", state.currentChars());

        Prompt prompt = new Prompt(List.of(
                new SystemMessage("""                             
                             ## 最大压缩限制（必须遵守）
                             - 你输出的最终内容【总字符数（包含所有标签、空格、换行）】
                                不得超过：%s
                             - 这是硬性上限，不是建议
                             - 如超过该限制，视为压缩失败
                        
                        """.formatted(contextCharLimit) + PlanExecutePromptsFactory.buildPrompts(planExecutePrompts).getCompressPrompt()),

                new UserMessage(renderMessages(state.getMessages()))
        ));

        String snapshot = chatModel.call(prompt)
                .getResult()
                .getOutput()
                .getText();

        state.clearMessages();
        state.add(new SystemMessage("【Compressed Agent State】\n" + snapshot));
        log.warn("===== Context compress has completed, size is {} =====", state.currentChars());
    }


    private String summarize(OverAllState state) {
        Prompt prompt = new Prompt(List.of(
                new SystemMessage(PlanExecutePromptsFactory.buildPrompts(planExecutePrompts).getSummarizePrompt()),
                new UserMessage("""
                        【用户原始问题】
                        %s
                        
                        【执行上下文（含工具结果）】
                        %s
                        """.formatted(
                        state.getQuestion(),
                        renderMessages(state.getMessages())
                ))
        ));

        String answer = chatModel.call(prompt).getResult().getOutput().getText();
        // 追加记忆
        if (state.conversationId != null && chatMemory != null) {
            chatMemory.add(state.conversationId, new AssistantMessage(answer));
        }
        return answer;
    }

    private String renderMessages(List<Message> messages) {
        StringBuilder sb = new StringBuilder();
        for (Message m : messages) {
            sb.append("\n\n[").append(m.getMessageType()).append("]\n\n")
                    .append(m.getText());
        }
        return sb.toString();
    }

    // 内部对象实体
    public record PlanTask(String id, String instruction, int order) {
    }

    public record CritiqueResult(boolean passed, String feedback) {
    }

    @Getter
    public static class OverAllState {

        private final String conversationId;
        private final String question;
        private final List<Message> messages = new ArrayList<>();
        private int round = 0;

        public OverAllState(String conversationId, String question) {
            this.question = question;
            this.conversationId = conversationId;
        }

        public void nextRound() {
            round++;
        }

        public void add(Message m) {
            messages.add(m);
        }

        public int currentChars() {
            return messages.stream()
                    .mapToInt(m -> m.getText() == null ? 0 : m.getText().length())
                    .sum();
        }

        public void clearMessages() {
            messages.clear();
        }
    }

//    public record PlanRoundState(
//            int round,
//            List<PlanTask> plan,
//            Map<String, TaskResult> results,
//            CritiqueResult critique
//    ) {
//    }

    public record TaskResult(
            String taskId,
            boolean success,
            String output,
            String error
    ) {
    }

    public static void main(String[] args) {
        ChatModel chatModel = ChatModelConfig.getChatModel();

        ToolCallback[] toolCallbacks = ToolCallbacks.from(new WeatherService(), new SearchService());

        ChatMemory chatMemory = MessageWindowChatMemory.builder().maxMessages(20).build();

        PlanExecuteAgent agent = PlanExecuteAgent.builder()
                .chatModel(chatModel)
                .tools(toolCallbacks)
                .maxRounds(3)
                .maxToolRetries(2)
                .chatMemory(chatMemory)
                .contextCharLimit(1000).build();

        String result = agent.call("""
                请你先查询北京今天的天气，再搜索本周末北京天气的预警情况，并基于本周末北京的天气预警情况，搜索北京本周末适合旅游打卡的景点有哪些，最终生成一份不少于 500 字的综合天气分析报告。
                """);

        System.out.println("\n===== FINAL ANSWER =====");
        System.out.println(result);

//        System.out.println(agent.call("123", "我的名字叫bigchui"));
//        System.out.println(agent.call("123", "我的名字是什么？"));
    }
}
