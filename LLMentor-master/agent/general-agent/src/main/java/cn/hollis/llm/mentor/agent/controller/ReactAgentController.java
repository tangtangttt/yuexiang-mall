package cn.hollis.llm.mentor.agent.controller;

import cn.hollis.llm.mentor.agent.tools.StockTools;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.checkpoint.savers.MemorySaver;
import com.alibaba.cloud.ai.graph.checkpoint.savers.mysql.MysqlSaver;
import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import javax.sql.DataSource;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Calendar;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/react")
public class ReactAgentController {

    @Autowired
    private ChatMemory chatMemory;

    @Autowired
    private ChatModel chatModel;

    @Autowired
    ToolCallingManager toolCallingManager;


    @GetMapping("/call")
    public String call(String conversationId) {
        //定义ChatOptions
        ChatOptions chatOptions = ToolCallingChatOptions.builder()
                //指定工具
                .toolCallbacks(ToolCallbacks.from(new StockTools()))
//                .model("deepseek-v3")
                .build();

        //定义提示词
        Prompt prompt = new Prompt(
                List.of(new SystemMessage("你是一个智能助手，你擅长使用工具帮我解决问题。" +
                        "约束：时间通过工具获取，不要捏造"), new UserMessage("帮我分析最近三个月特斯拉（TSLA）的股价走势，并结合新闻事件解释可能的影响因素。" +
                        "" +
                        "今天是:" + Calendar.getInstance().getTime())),
                chatOptions);

        //添加提示词到记忆
        chatMemory.add(conversationId, prompt.getInstructions());

        Prompt promptWithMemory = new Prompt(chatMemory.get(conversationId), chatOptions);

        //调用模型
        ChatResponse chatResponse = chatModel.call(promptWithMemory);

        return chatResponse.getResult().getOutput().getText();
    }

    @GetMapping("/chatWithSpringAi")
    public String chatWithSpringAi(String conversationId) {
        //定义ChatOptions
        ChatOptions chatOptions = ToolCallingChatOptions.builder()
                //指定工具
                .toolCallbacks(ToolCallbacks.from(new StockTools()))
                //指定不自动执行工具
                .internalToolExecutionEnabled(false)
                .build();

        //定义提示词，要求按照React架构运行
        Prompt prompt = new Prompt(
                List.of(new SystemMessage("你是一个基于React架构（Reasoning-Act-Observation）的智能助手，你擅长使用工具帮我解决问题。" +
                        "你的工作流程是：" +
                        "1、思考：先根据用户的提问进行思考，推理出下一步需要进行的具体系统" +
                        "2、行动：做具体的行动，这一步可以使用工具" +
                        "3、观察：记录前一步行动的结果。你可以进行多轮思考和行动。如果要使用工具，请务必调用工具，不要自己随便捏造结果。"
                        + "约束：时间通过工具获取，不要捏造"), new UserMessage("帮我分析最近三个月特斯拉（TSLA）的股价走势，并结合新闻事件解释可能的影响因素。")),
                chatOptions);

        //添加提示词到记忆
        chatMemory.add(conversationId, prompt.getInstructions());

        Prompt promptWithMemory = new Prompt(chatMemory.get(conversationId), chatOptions);

        //调用模型
        ChatResponse chatResponse = chatModel.call(promptWithMemory);

        //添加模型返回结果到记忆
        chatMemory.add(conversationId, chatResponse.getResult().getOutput());

        //循环处理工具调用
        while (chatResponse.hasToolCalls()) {
            //执行工具调用
            ToolExecutionResult toolExecutionResult = toolCallingManager.executeToolCalls(promptWithMemory,
                    chatResponse);

            //添加工具调用结果到记忆
            chatMemory.add(conversationId, toolExecutionResult.conversationHistory()
                    .get(toolExecutionResult.conversationHistory().size() - 1));

            //创建新的提示词
            promptWithMemory = new Prompt(chatMemory.get(conversationId), chatOptions);

            //调用模型
            chatResponse = chatModel.call(promptWithMemory);

            //添加模型返回结果到记忆
            chatMemory.add(conversationId, chatResponse.getResult().getOutput());
        }

        for (Message message11 : chatMemory.get(conversationId)) {
            System.out.println(message11);
        }

        return chatResponse.getResult().getOutput().getText();
    }

    @GetMapping("/chatWithSpringAiAlibaba")
    public String chatWithSpringAiAlibaba(String conversationId) throws GraphRunnerException {

        String systemPrompt = String.format("你是一个智能助手，你擅长使用工具帮我解决问题。" +
                "你的工作流程是：" +
                "1、思考：先根据用户的提问进行思考，推理出下一步需要进行的具体系统" +
                "2、行动：做具体的行动，这一步可以使用工具" +
                "3、观察：记录前一步行动的结果。你可以进行多轮思考和行动。如果要使用工具，请务必调用工具，不要自己随便捏造结果。");

        ReactAgent agent = ReactAgent.builder()
                .name("executor")
                .model(chatModel)
                .tools(ToolCallbacks.from(new StockTools()))
                .systemPrompt(systemPrompt)
                .saver(new MemorySaver())
                .build();

        RunnableConfig config = RunnableConfig.builder()
                .threadId(conversationId)
                .build();

        AssistantMessage chatResponse = agent.call("帮我分析最近三个月特斯拉（TSLA）的股价走势，并结合新闻事件解释可能的影响因素。", config);

        return chatResponse.getText();
    }


    @Autowired
    private DataSource dataSource;

    @GetMapping("/chatWithMySqlSaver")
    public String chatWithMySqlSaver(String conversationId,String msg) throws GraphRunnerException {

        String systemPrompt = String.format("你是一个智能助手");

        ReactAgent agent = ReactAgent.builder()
                .name("executor")
                .model(chatModel)
                .systemPrompt(systemPrompt)
                .saver(new MysqlSaver.Builder().dataSource(dataSource).build())
                .build();

        RunnableConfig config = RunnableConfig.builder()
                .threadId(conversationId)
                .build();

        AssistantMessage chatResponse = agent.call(msg, config);

        return chatResponse.getText();
    }

    @GetMapping("/streamWithSpringAiAlibaba")
    public Flux<String> streamWithSpringAiAlibaba(String conversationId) throws GraphRunnerException {

        String systemPrompt = String.format("你是一个基于React架构（Reasoning-Act-Observation）的智能助手，你擅长使用工具帮我解决问题。" +
                "你的工作流程是：" +
                "1、思考：先根据用户的提问进行思考，推理出下一步需要进行的具体系统" +
                "2、行动：做具体的行动，这一步可以使用工具" +
                "3、观察：记录前一步行动的结果。你可以进行多轮思考和行动。如果要使用工具，请务必调用工具，不要自己随便捏造结果。");

        ReactAgent agent = ReactAgent.builder()
                .name("executor")
                .model(chatModel)
                .tools(ToolCallbacks.from(new StockTools()))
                .systemPrompt(systemPrompt)
                .saver(new MemorySaver())
                .build();

        RunnableConfig config = RunnableConfig.builder()
                .threadId(conversationId)
                .build();

        return agent.stream("帮我分析最近三个月特斯拉（TSLA）的股价走势，并结合新闻事件解释可能的影响因素。", config)
                .map(output -> {
                    if (output instanceof StreamingOutput) {
                        Message message = ((StreamingOutput<?>) output).message();
                        return message != null ? message.getText() : "";
                    } else {
                        String nodeId = output.node();
                        Map<String, Object> state = output.state().data();
                        return "节点 '" + nodeId + "' 执行完成\n";
                    }
                })
                .filter(text -> !text.isEmpty());
    }

    @GetMapping("/callWithMcpAndAgent")
    public Mono<String> callWithMcpAndAgent(String city) {
        return Mono.fromCallable(() -> {
            // 配置自定义 HttpClient，增加超时时间和错误处理
            HttpClientStreamableHttpTransport streamableTransport = HttpClientStreamableHttpTransport
                    .builder("http://127.0.0.1:8004/stream/test/")
                    .endpoint("api/mcp")
                    .clientBuilder(HttpClient.newBuilder()
                            .connectTimeout(Duration.ofSeconds(60))  // 连接超时60秒
                            .version(HttpClient.Version.HTTP_1_1))   // 使用 HTTP/1.1 更稳定
                    .build();

            McpSyncClient streamableClient = McpClient.sync(streamableTransport)
                    .clientInfo(new io.modelcontextprotocol.spec.McpSchema.Implementation("streamable-client", "1.0"))
                    .requestTimeout(Duration.ofSeconds(60))  // 增加请求超时到60秒
                    .build();

            try {
                // 在弹性调度器中执行阻塞调用
                streamableClient.initialize();
            } catch (Exception e) {
                throw new RuntimeException("MCP Client 初始化失败: " + e.getMessage(), e);
            }

            List<McpSyncClient> clients = List.of(streamableClient);

            SyncMcpToolCallbackProvider provider = SyncMcpToolCallbackProvider.builder()
                    .mcpClients(clients)
                    .build();

            ToolCallback[] callbacks = provider.getToolCallbacks();

            // 创建 Agent
            ReactAgent agent = ReactAgent.builder()
                    .name("demo_agent")
                    .model(chatModel)
                    .tools(callbacks)
                    .systemPrompt("你是一个助手。请根据用户的问题进行回答。")
                    .build();

            try {
                return agent.call("今天" + city + "的天气如何？").getText();
            } finally {
                // 关闭 MCP 客户端连接
                try {
                    streamableClient.closeGracefully();
                } catch (Exception e) {
                    // 忽略关闭异常
                }
            }
        }).subscribeOn(Schedulers.boundedElastic());  // 在弹性调度器上执行阻塞操作
    }

}
