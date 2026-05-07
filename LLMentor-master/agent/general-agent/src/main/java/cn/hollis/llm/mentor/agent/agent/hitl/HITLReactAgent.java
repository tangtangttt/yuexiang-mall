package cn.hollis.llm.mentor.agent.agent.hitl;

import cn.hollis.llm.mentor.agent.config.ChatModelConfig;
import cn.hollis.llm.mentor.agent.tools.SearchService;
import cn.hollis.llm.mentor.agent.tools.WeatherService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.messages.*;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.model.SimpleApiKey;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class HITLReactAgent {
    public static final String REACT_AGENT_SYSTEM_PROMPT = """
            ## 角色
            你是一个严格遵循 ReAct 模式的智能 AI 助手，会通过 Reasoning → Act(ToolCall) → Observation 的反复循环来逐步解决任务。

            ## 工具调用规则（极其重要）
            1. 如果需要调用工具：必须使用 OpenAI 官方 ToolCall 结构，并且 **只能通过工具调用字段输出**。
            2. 工具调用时：**禁止在 content 中出现任何形式的工具调用文本**（包括 JSON、<tool_call>、函数名、参数、思考、推理或描述）。
            3. 工具调用消息必须是一次性、原子性输出，不得混杂任何解释或内容。
            4. 工具调用前后不得输出任何多余文字、标签、换行、推理轨迹或说明。
            5. 调用工具时：
               -工具参数必须是有效的JSON
               -参数必须简洁，不超过500个字符
               -切勿包含以前的工具结果、原始内容、HTML或长文本
               -仅包括工具所需的最小控制参数

            ## 工具执行结果
            系统会自动将工具执行结果作为 ToolResponseMessage 注入上下文，你只需读取并决定下一步动作。

            ## 最终答案规则
            1. 如果上下文已经拥有了完成任务的全部信息，则不要再调用任何工具。
            2. 在这种情况下，你必须输出最终自然语言答案，且 **禁止包含任何工具调用格式**。
            3. 最终答案只允许是自然语言，不能包含 JSON、思考过程、reasoning、ToolCall 或伪代码。

            ## 强制要求（必须遵守）
            1. 工具调用消息必须只通过 ToolCall 字段输出，不允许在 content 字段体现工具调用迹象。
            2. 如果本轮没有工具调用，则视为任务完成，你必须输出最终答案。
            3. 不允许重复调用同一个工具（名称 + 参数完全一致），除非工具调用失败。
            4. 禁止输出会干扰工具系统解析的任何结构（如 <reason>、<ToolCall>、函数 JSON、或模型内部思考）。
            5. 如果上下文已经包含了完成任务的全部信息，则不要再调用任何工具。
                        
            ## 反思机制
            如果在反思过程中，助手判断当前回答未能完全满足用户问题，或者达到最大反思轮次，你必须遵循以下规则：
            1. 尽最大可能利用当前已有的信息给出完整回答，即使信息不完全，也要合理推断或总结现有数据。
            2. 如果某些关键信息缺失，可在答案中用合理措辞提示用户，如“根据现有信息判断…”或“可进一步确认…”。
            3. 最终输出必须尽量满足用户需求，保证逻辑清晰、结论可靠、表达完整，即便未能完美覆盖所有反思反馈。
            """;

    private final String name;
    private final ChatModel chatModel;
    private final List<ToolCallback> tools;
    private ChatClient chatClient;
    private final List<Advisor> advisors;

    private ObjectMapper objectMapper = new ObjectMapper();

    private int maxRounds;

    public HITLReactAgent(String name, ChatModel chatModel, List<ToolCallback> tools, List<Advisor> advisors, int maxRounds) {
        this.name = name;
        this.chatModel = chatModel;
        this.tools = tools;
        this.advisors = advisors;
        this.maxRounds = maxRounds;

        initChatClient();

        if (this.chatClient == null) {
            throw new IllegalStateException("ChatClient 初始化失败！");
        }
    }

    private void initChatClient() {
        try {
            ToolCallingChatOptions toolOptions = ToolCallingChatOptions.builder()
                    .toolCallbacks(tools)
                    .internalToolExecutionEnabled(false)
                    .build();

            this.chatClient = ChatClient.builder(chatModel)
                    .defaultOptions(toolOptions)
                    .defaultAdvisors(advisors == null ? new ArrayList() : advisors)
                    .defaultToolCallbacks(tools)
                    .build();
        } catch (Exception e) {
            throw new RuntimeException("ChatClient 初始化失败：" + e.getMessage(), e);
        }
    }

    /**
     * 非流式输出
     *
     * @param question
     * @return
     */
    public AgentResult call(String question) {

        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(REACT_AGENT_SYSTEM_PROMPT));
        messages.add(new UserMessage(question));

        Map<String, Object> context = new ConcurrentHashMap<>();
        context.put(HITLAdvisor.HITL_STATE_KEY, new HITLState());

        return run(messages, context);
    }

    private AgentResult run(List<Message> messages, Map<String, Object> context) {

        int round = 0;

        while (true) {
            round++;
            if (maxRounds > 0 && round > maxRounds) {
                return new AgentFinished(chatClient.prompt().messages(messages).call().content());
            }

            ChatClientResponse response = chatClient.prompt().messages(messages).call().chatClientResponse();

            // 增加判断HITL_REQUIRED，说明需要人工介入，返回中断元数据
            if (Boolean.TRUE.equals(response.context().get(HITLAdvisor.HITL_REQUIRED))) {
                return new AgentInterrupted(
                        (List<PendingToolCall>) response.context().get(HITLAdvisor.HITL_PENDING_TOOLS),
                        List.copyOf(messages),
                        context
                );
            }

            if (!response.chatResponse().hasToolCalls()) {
                return new AgentFinished(response.chatResponse().getResult().getOutput().getText());
            }

            AssistantMessage assistant = AssistantMessage.builder()
                    .toolCalls(response.chatResponse()
                            .getResult()
                            .getOutput()
                            .getToolCalls()).build();

            messages.add(assistant);

            for (AssistantMessage.ToolCall tc : assistant.getToolCalls()) {

                ToolCallback tool = findTool(tc.name());
                String result = tool.call(tc.arguments());

                messages.add(ToolResponseMessage.builder().responses(
                        List.of(new ToolResponseMessage.ToolResponse(tc.id(), tc.name(), result))).build());
            }
        }
    }

    public AgentResult resume(AgentInterrupted interrupted, List<PendingToolCall> feedbacks) {

        List<Message> messages = new ArrayList<>(interrupted.checkpointMessages());
        Map<String, Object> context = interrupted.context();

        HITLState hitlState = (HITLState) context.get(HITLAdvisor.HITL_STATE_KEY);

        List<AssistantMessage.ToolCall> toolCalls = new ArrayList<>();

        for (PendingToolCall fb : feedbacks) {
            // 过滤已处理的工具调用，避免重复 HITL
            if (hitlState.isConsumed(fb.id())) {
                continue;
            }
            // 标记为已处理
            hitlState.markConsumed(fb.id());

            toolCalls.add(new AssistantMessage.ToolCall(fb.id(), "function", fb.name(), fb.arguments()));
        }

        if (!toolCalls.isEmpty()) {
            // 补全tool_call消息，确保工具调用参数完整
            messages.add(AssistantMessage.builder().toolCalls(toolCalls).build());
        }

        for (PendingToolCall fb : feedbacks) {
            // 将消费过的工具调用结果添加到消息中
            if (hitlState.isConsumed(fb.id())) {
                String result;
                if (fb.result() == PendingToolCall.FeedbackResult.REJECTED) {
                    result = "用户不同意执行此工具，工具名称：" + fb.name() + "，工具描述：" + fb.description();
                } else {
                    // 这边同意和编辑简单处理，实际可以让用户重新编辑arguments
                    ToolCallback tool = findTool(fb.name());
                    result = tool.call(fb.arguments());
                }

                messages.add(ToolResponseMessage.builder().responses(List.of(new ToolResponseMessage.ToolResponse(fb.id(), fb.name(), result))).build());
            }
        }

        // 继续执行主循环
        return run(messages, context);
    }

    private ToolCallback findTool(String name) {
        return tools.stream()
                .filter(t -> t.getToolDefinition().name().equals(name))
                .findFirst()
                .orElse(null);
    }

    public static Builder builder() {
        return new Builder();
    }


    public static class Builder {
        private String name;
        private ChatModel chatModel;
        private List<ToolCallback> tools;

        private int maxRounds;

        private List<Advisor> advisors;

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder chatModel(ChatModel chatModel) {
            this.chatModel = chatModel;
            return this;
        }

        public Builder tools(ToolCallback... tools) {
            this.tools = Arrays.asList(tools);
            return this;
        }

        public Builder tools(List<ToolCallback> tools) {
            this.tools = tools;
            return this;
        }

        public Builder advisors(List<Advisor> advisors) {
            this.advisors = advisors;
            return this;
        }

        public Builder advisors(Advisor... advisors) {
            this.advisors = Arrays.asList(advisors);
            return this;
        }

        public Builder maxRounds(int maxRounds) {
            this.maxRounds = maxRounds;
            return this;
        }

        public HITLReactAgent build() {
            if (chatModel == null) {
                throw new IllegalArgumentException("chatModel 不能为空！");
            }
            return new HITLReactAgent(name, chatModel, tools, advisors, maxRounds);
        }
    }

    public static void main(String[] args) {
        ChatModel chatModel = ChatModelConfig.getChatModel();

        ToolCallback[] toolCallbacks =
                ToolCallbacks.from(new WeatherService(), new SearchService());

        // 拦截 getWeather 和 search
        HITLAdvisor hitlAdvisor = new HITLAdvisor(Set.of("getWeather", "search"));

        HITLReactAgent agent = HITLReactAgent.builder()
                .name("HITLReactAgent")
                .chatModel(chatModel)
                .advisors(List.of(hitlAdvisor))
                .tools(Arrays.stream(toolCallbacks).toList())
                .build();

        // 第一次 call
        AgentResult result = agent.call("北京今天的天气如何？并搜索下北京有什么好吃的饭店？");

        // 多次 HITL 处理
        while (result instanceof AgentInterrupted interrupted) {

            System.out.println("===== HITL 中断 =====");

            for (PendingToolCall tc : interrupted.pendingToolCalls()) {
                System.out.println("=================== 需要用户审批的工具： =================");
                System.out.println("工具: " + tc.name());
                System.out.println("参数: " + tc.arguments());
            }
            System.out.println("=================== 以上工具需要用户审批 ===================");

            // 模拟人工审批
            List<PendingToolCall> feedbacks = new ArrayList<>();
            List<PendingToolCall> pendingToolCalls = interrupted.pendingToolCalls();
            for (PendingToolCall tc : pendingToolCalls) {
                System.out.println("请输入审批结果（同意/拒绝）：");
                Scanner scanner = new Scanner(System.in);
                String approval = scanner.nextLine();
                if (approval.equalsIgnoreCase("同意")) {
                    feedbacks.add(tc.approve());
                } else {
                    feedbacks.add(tc.reject("用户拒绝使用"));
                }
            }
//            List<PendingToolCall> feedbacks = interrupted.pendingToolCalls().stream()
//                    .map(tc -> new PendingToolCall(tc.id(), tc.name(), tc.arguments(), PendingToolCall.FeedbackResult.REJECTED, "拒绝使用"))
//                    .toList();

            // 再次发起调用
            result = agent.resume(interrupted, feedbacks);
        }

        if (result instanceof AgentFinished finished) {
            System.out.println("===== 最终结果 =====");
            System.out.println(finished.content());
        }
    }

}
