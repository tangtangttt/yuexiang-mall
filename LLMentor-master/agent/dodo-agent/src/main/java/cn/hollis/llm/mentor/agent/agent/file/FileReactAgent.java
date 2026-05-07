package cn.hollis.llm.mentor.agent.agent.file;

import cn.hollis.llm.mentor.agent.agent.BaseAgent;
import cn.hollis.llm.mentor.agent.entity.record.RoundMode;
import cn.hollis.llm.mentor.agent.entity.record.RoundState;
import cn.hollis.llm.mentor.agent.prompts.ReactAgentPrompts;
import cn.hollis.llm.mentor.agent.entity.record.FileInfo;
import cn.hollis.llm.mentor.agent.entity.AiSession;
import cn.hollis.llm.mentor.agent.entity.vo.SaveQuestionRequest;
import cn.hollis.llm.mentor.agent.entity.vo.UpdateAnswerRequest;
import cn.hollis.llm.mentor.agent.service.AgentTaskManager;
import cn.hollis.llm.mentor.agent.service.AiSessionService;
import cn.hollis.llm.mentor.agent.service.FileManageService;
import cn.hollis.llm.mentor.agent.service.MinioService;
import cn.hollis.llm.mentor.agent.utils.AppContextClient;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.*;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.content.Media;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.util.MimeTypeUtils;
import org.springframework.util.StringUtils;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;

import java.io.InputStream;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static cn.hollis.llm.mentor.agent.service.FileManageService.generateObjectName;

/**
 * 文件问答智能体
 * 基于文件内容进行问答分析
 * 支持多种文件类型：PDF、DOC、DOCX、TXT、PNG、JPG等
 */
@Slf4j
public class FileReactAgent extends BaseAgent {

    private ChatClient chatClient;
    private final List<ToolCallback> tools;
    private final String systemPrompt;
    private int maxRounds;
    private String currentFileId;

    private boolean enableRecommendations = false;

    public FileReactAgent(String name, ChatModel chatModel, List<ToolCallback> tools,
                          String systemPrompt, int maxRounds, ChatMemory chatMemory,
                          AiSessionService sessionService, AgentTaskManager taskManager) {
        super(name, chatModel, "file");
        this.tools = tools;
        this.systemPrompt = systemPrompt;
        this.maxRounds = maxRounds;
        this.chatMemory = chatMemory;
        this.sessionService = sessionService;
        this.taskManager = taskManager;

        // 初始化工具记录集合
        this.usedTools = new HashSet<>();

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

            ChatClient.Builder builder = ChatClient.builder(chatModel);
            this.chatClient = builder.defaultOptions(toolOptions).defaultToolCallbacks(tools).build();
        } catch (Exception e) {
            throw new RuntimeException("ChatClient 初始化失败：" + e.getMessage(), e);
        }
    }

    @Override
    public Flux<String> execute(String conversationId, String question) {
        return streamInternal(conversationId, question);
    }

    /**
     * 设置当前处理的文件ID
     */
    public void setCurrentFileId(String fileId) {
        this.currentFileId = fileId;
    }

    /**
     * 获取当前文件ID
     */
    public String getCurrentFileId() {
        return currentFileId;
    }

    /**
     * 流式输出（带文件ID）
     */
    public Flux<String> stream(String conversationId, String question, String fileId) {
        setCurrentFileId(fileId);
        return streamInternal(conversationId, question);
    }

    /**
     * 内部流式处理方法
     */
    private Flux<String> streamInternal(String conversationId, String question) {
        List<Message> messages = Collections.synchronizedList(new ArrayList<>());
        boolean useMemory = conversationId != null && chatMemory != null;

        // 检查是否已有任务在执行
        Flux<String> checkResult = checkRunningTask(conversationId);
        if (checkResult != null) {
            return checkResult;
        }

        Sinks.Many<String> sink = Sinks.many().unicast().onBackpressureBuffer();

        // 注册任务到管理器
        AgentTaskManager.TaskInfo taskInfo = registerTask(conversationId, sink);
        if (taskInfo == null && conversationId != null && taskManager != null) {
            return Flux.error(new IllegalStateException("该会话正在执行中，请稍后再试"));
        }

        // 初始化计时器
        initTimers();
        clearUsedTools();

        // ===== 加载 System Prompt（始终放在最开始）=====
        messages.add(new SystemMessage(ReactAgentPrompts.getFilePrompt()));
        if (StringUtils.hasText(systemPrompt)) {
            messages.add(new SystemMessage(systemPrompt));
        }

        // ===== 加载历史记忆 =====
        loadChatHistory(conversationId, messages, true, true);

        // ===== 加载文件内容或文件信息 =====
        // 注释掉原有的 loadFileContent 调用，使用新的 FileContentService 工具替代
        // UserMessage userMessage = loadFileContent();
        // if (userMessage != null && StringUtils.hasText(userMessage.getText()))
        //     messages.add(userMessage);;

        messages.add(new UserMessage("<question>" + question + "</question>"));
        messages.add(new UserMessage("<fileid>" + currentFileId + "</fileid>"));
        currentQuestion = question;

        // 添加记忆并保存到数据库
        if (sessionService != null) {
            // 保存用户问题到数据库，关联fileid
            AiSession savedSession = sessionService.saveQuestion(
                    SaveQuestionRequest.builder()
                            .sessionId(conversationId)
                            .question(question)
                            .fileid(currentFileId)
                            .build()
            );
            currentSessionId = savedSession.getId();
        }

        // 迭代轮次
        AtomicLong roundCounter = new AtomicLong(0);
        // 是否发送最终结果标记位
        AtomicBoolean hasSentFinalResult = new AtomicBoolean(false);

        hasSentFinalResult.set(false);
        roundCounter.set(0);

        // 收集最终答案，存储memory
        StringBuilder finalAnswerBuffer = new StringBuilder();
        // 收集思考过程
        StringBuilder thinkingBuffer = new StringBuilder();

        scheduleRound(messages, sink, roundCounter, hasSentFinalResult, finalAnswerBuffer, useMemory, conversationId, thinkingBuffer);

        return sink.asFlux()
                // 分离收集 text 和 thinking
                .doOnNext(chunk -> {
                    try {
                        JSONObject json = JSON.parseObject(chunk);
                        String type = json.getString("type");
                        if ("text".equals(type)) {
                            finalAnswerBuffer.append(json.getString("content"));
                        } else if ("thinking".equals(type)) {
                            thinkingBuffer.append(json.getString("content"));
                        }
                    } catch (Exception e) {
                        // 解析失败，直接拼接
                        finalAnswerBuffer.append(chunk);
                    }
                })
                .doOnCancel(() -> {
                    hasSentFinalResult.set(true);
                    if(taskManager != null){
                        taskManager.stopTask(conversationId);
                    }
                })
                .doFinally(signalType -> {
                    log.info("最终答案: {}", finalAnswerBuffer);
                    log.info("思考过程: {}", thinkingBuffer);

                    // 保存结果到会话
                    saveSessionResult(conversationId, finalAnswerBuffer, thinkingBuffer);

                    // 流结束时移除任务
                    if(taskManager != null){
                        taskManager.stopTask(conversationId);
                    }
                });
    }

    /**
     * 保存会话结果
     */
    private void saveSessionResult(String conversationId, StringBuilder finalAnswerBuffer, StringBuilder thinkingBuffer) {
        if (sessionService != null && currentSessionId != null && finalAnswerBuffer.length() > 0) {
            long totalResponseTime = getTotalResponseTime();
            String toolsStr = getUsedToolsString();
            UpdateAnswerRequest request = UpdateAnswerRequest.builder()
                    .id(currentSessionId)
                    .answer(finalAnswerBuffer.toString())
                    .thinking(thinkingBuffer.toString())
                    .tools(toolsStr)
                    .recommend(currentRecommendations)
                    .firstResponseTime(firstResponseTime)
                    .totalResponseTime(totalResponseTime)
                    .build();
            sessionService.updateAnswer(request);
            log.info("结果已保存到会话: sessionId={}", conversationId);
        }
    }

    /**
     * 加载文件内容并构建用户消息
     */
    private UserMessage loadFileContent() {
        // 1. 获取依赖服务
        FileManageService fileManageService = AppContextClient.getBean(FileManageService.class);
        MinioService minioService = AppContextClient.getBean(MinioService.class);

        // 2. 查询文件信息
        FileInfo fileInfo = fileManageService.getFileInfo(currentFileId);
        if (fileInfo == null) {
            return UserMessage.builder().text("文件信息不存在，请检查文件ID是否正确").build();
        }

        if (isImageFile(fileInfo.getFileType())) {
            return handleImageFile(fileInfo, minioService);
        } else {
            return handleTextFile(fileInfo);
        }
    }

    /**
     * 处理图片文件
     */
    private UserMessage handleImageFile(FileInfo fileInfo, MinioService minioService) {
        String fileId = fileInfo.getFileId();
        String fileType = fileInfo.getFileType();

        try {
            // 生成MinIO对象名称
            String objectName = generateObjectName(fileId, fileType);

            try (InputStream inputStream = minioService.downloadFile(objectName)) {
                // 读取文件字节数组
                byte[] fileBytes = IOUtils.toByteArray(inputStream);

                // 校验文件字节是否为空
                if (fileBytes == null || fileBytes.length == 0) {
                    return UserMessage.builder().text("图片文件内容为空，请检查文件是否上传完整").build();
                }

                // 构建图片消息
                ByteArrayResource imageResource = new ByteArrayResource(fileBytes);
                List<Media> mediaList = Collections.singletonList(
                        new Media(MimeTypeUtils.IMAGE_PNG, imageResource)
                );

                return UserMessage.builder()
                        .text("当前文件是一张图片，请围绕这个文件进行问答：")
                        .media(mediaList)
                        .build();
            }
        } catch (Exception e) {
            return UserMessage.builder().text("图片文件处理失败：" + e.getMessage()).build();
        }
    }

    /**
     * 处理文本文件
     */
    private UserMessage handleTextFile(FileInfo fileInfo) {
        String extractedText = fileInfo.getExtractedText();
        // 校验文本内容是否为空，提升用户体验
        String textContent = (extractedText == null || extractedText.trim().isEmpty())
                ? "当前文件是一个文本文件，但文件内容为空，请检查文件是否有效。"
                : "当前文件是一个文本文件，请围绕这个文件进行问答，以下是这个文件的具体内容：\n" + extractedText;

        return UserMessage.builder()
                .text(textContent)
                .build();
    }

    /**
     * 判断是否为图片文件
     */
    private boolean isImageFile(String fileType) {
        return ("jpg".equalsIgnoreCase(fileType) ||
                "jpeg".equalsIgnoreCase(fileType) ||
                "png".equalsIgnoreCase(fileType) ||
                "gif".equalsIgnoreCase(fileType) ||
                "bmp".equalsIgnoreCase(fileType));
    }

    private void scheduleRound(List<Message> messages, Sinks.Many<String> sink,
                               AtomicLong roundCounter, AtomicBoolean hasSentFinalResult,
                               StringBuilder finalAnswerBuffer, boolean useMemory, String conversationId, StringBuilder thinkingBuffer) {
        // 轮次+1
        roundCounter.incrementAndGet();
        RoundState state = new RoundState();

        Disposable disposable = chatClient.prompt()
                .messages(messages)
                .stream()
                .chatResponse()
                .publishOn(Schedulers.boundedElastic())
                .doOnNext(chunk -> processChunk(chunk, sink, state))
                .doOnComplete(() -> finishRound(messages, sink, state, roundCounter,
                        hasSentFinalResult, finalAnswerBuffer, useMemory, conversationId, thinkingBuffer))
                .doOnError(err -> {
                    if (!hasSentFinalResult.get()) {
                        hasSentFinalResult.set(true);
                        sink.tryEmitError(err);
                    }
                })
                .subscribe();

        // 保存Disposable到任务管理器
        if (conversationId != null && taskManager != null) {
            taskManager.setDisposable(conversationId, disposable);
        }
    }

    private void processChunk(ChatResponse chunk, Sinks.Many<String> sink, RoundState state) {
        if (chunk == null || chunk.getResult() == null ||
                chunk.getResult().getOutput() == null) {
            return;
        }

        Generation gen = chunk.getResult();
        String text = gen.getOutput().getText();
        List<AssistantMessage.ToolCall> tc = gen.getOutput().getToolCalls();

        // 一旦发现 tool_call，立即进入 TOOL_CALL 模式
        if (tc != null && !tc.isEmpty()) {
            state.setMode(RoundMode.TOOL_CALL);

            for (AssistantMessage.ToolCall incoming : tc) {
                mergeToolCall(state, incoming);
            }
            return;
        }

        // 还没出现 tool_call，发送并缓存文本
        if (text != null) {
            sink.tryEmitNext(createTextResponse(text));
            state.getTextBuffer().append(text);
        }
    }

    private void mergeToolCall(RoundState state, AssistantMessage.ToolCall incoming) {
        for (int i = 0; i < state.toolCalls.size(); i++) {
            AssistantMessage.ToolCall existing = state.toolCalls.get(i);

            if (existing.id().equals(incoming.id())) {
                String mergedArgs = Objects.toString(existing.arguments(), "") + Objects.toString(incoming.arguments(), "");
                state.getToolCalls().set(i,
                        new AssistantMessage.ToolCall(existing.id(), "function", existing.name(), mergedArgs)
                );
                return;
            }
        }

        // 新的 toolcall
        state.getToolCalls().add(incoming);
    }

    /**
     * 轮次结束处理工具调用
     */
    private void finishRound(List<Message> messages, Sinks.Many<String> sink,
                             RoundState state, AtomicLong roundCounter,
                             AtomicBoolean hasSentFinalResult, StringBuilder finalAnswerBuffer,
                             boolean useMemory, String conversationId, StringBuilder thinkingBuffer) {

        // 如果整轮都没有 tool_call，才是最终答案
        if (state.getMode() != RoundMode.TOOL_CALL) {
            sink.tryEmitComplete();
            hasSentFinalResult.set(true);

            long totalResponseTime = getTotalResponseTime();
            String toolsStr = getUsedToolsString();
            String finalText = state.getTextBuffer().toString();

            // 输出推荐问题
            if (enableRecommendations) {
                String recommendations = generateRecommendations(conversationId, currentQuestion, finalText);
                if (recommendations != null) {
                    currentRecommendations = recommendations; // 保存用于数据库存储
                    String recommendJson = createRecommendResponse(recommendations);
                    sink.tryEmitNext(recommendJson);
                }
            }
            sink.tryEmitComplete();
            hasSentFinalResult.set(true);
            return;
        }

        // TOOL_CALL
        AssistantMessage assistantMsg = AssistantMessage.builder().toolCalls(state.getToolCalls()).build();
        messages.add(assistantMsg);

        if (maxRounds > 0 && roundCounter.get() >= maxRounds) {
            forceFinalStream(messages, sink, hasSentFinalResult, state, conversationId, useMemory, thinkingBuffer);
            return;
        }

        executeToolCalls(sink, state.getToolCalls(), messages, hasSentFinalResult, state, () -> {
            if (!hasSentFinalResult.get()) {
                scheduleRound(messages, sink, roundCounter,
                        hasSentFinalResult, finalAnswerBuffer,
                        useMemory, conversationId, thinkingBuffer);
            }
        });
    }

    private void forceFinalStream(List<Message> messages, Sinks.Many<String> sink, AtomicBoolean hasSentFinalResult, RoundState state,
                                  String conversationId, boolean useMemory, StringBuilder thinkingBuffer) {
        // 创建新的消息列表，确保系统提示词在最前面
        List<Message> newMessages = new ArrayList<>();

        // 添加系统提示词
        newMessages.add(new SystemMessage(ReactAgentPrompts.getFilePrompt()));
        if (StringUtils.hasText(systemPrompt)) {
            newMessages.add(new SystemMessage(systemPrompt));
        }

        // 添加原有消息（跳过系统消息）
        for (Message msg : messages) {
            if (!(msg instanceof SystemMessage)) {
                newMessages.add(msg);
            }
        }

        // 添加限制提示
        newMessages.add(new UserMessage("""
                你已达到最大推理轮次限制。
                请基于当前已有的上下文信息，
                直接给出最终答案。
                禁止再调用任何工具。
                如果信息不完整，请合理总结和说明。
                """));

        // 替换原消息列表
        messages.clear();
        messages.addAll(newMessages);

        // 收集最终文本
        StringBuilder finalTextBuffer = new StringBuilder();

        Disposable disposable = chatClient.prompt()
                .messages(messages)
                .stream()
                .chatResponse()
                .publishOn(Schedulers.boundedElastic())
                .doOnNext(chunk -> {
                    if (chunk == null || chunk.getResult() == null || chunk.getResult().getOutput() == null) {
                        return;
                    }

                    String text = chunk.getResult()
                            .getOutput()
                            .getText();

                    if (text != null && !hasSentFinalResult.get()) {
                        sink.tryEmitNext(createTextResponse(text));
                        finalTextBuffer.append(text);
                    }
                })
                .doOnComplete(() -> {
                    String finalText = finalTextBuffer.toString();

                    // 输出推荐问题
                    if (enableRecommendations) {
                        String recommendations = generateRecommendations(conversationId, currentQuestion, finalText);
                        if (recommendations != null) {
                            currentRecommendations = recommendations; // 保存用于数据库存储
                            String recommendJson = createRecommendResponse(recommendations);
                            sink.tryEmitNext(recommendJson);
                        }
                    }

                    hasSentFinalResult.set(true);
                    sink.tryEmitComplete();
                })
                .doOnError(err -> {
                    hasSentFinalResult.set(true);
                    sink.tryEmitError(err);
                })
                .subscribe();

        // 保存Disposable到任务管理器
        if (conversationId != null && taskManager != null) {
            taskManager.setDisposable(conversationId, disposable);
        }
    }

    private void executeToolCalls(Sinks.Many<String> sink, List<AssistantMessage.ToolCall> toolCalls, List<Message> messages,
                                  AtomicBoolean hasSentFinalResult, RoundState state, Runnable onComplete) {
        AtomicInteger completedCount = new AtomicInteger(0);
        int totalToolCalls = toolCalls.size();

        for (AssistantMessage.ToolCall tc : toolCalls) {
            Schedulers.boundedElastic().schedule(() -> {
                if (hasSentFinalResult.get()) {
                    completeToolCall(completedCount, totalToolCalls, onComplete);
                    return;
                }

                String toolName = tc.name();
                String argsJson = tc.arguments();

                ToolCallback callback = findTool(toolName);
                if (callback == null) {
                    addErrorToolResponse(messages, tc, "工具未找到：" + toolName);
                    completeToolCall(completedCount, totalToolCalls, onComplete);
                    return;
                }

                // 如果是 loadContent 工具，解析参数并发送 thinking 消息
                if (toolName.contains("loadContent")) {
                    JSONObject args = JSON.parseObject(argsJson);
                    String question = (String) args.get("question");
                    // 发送 thinking 消息，表示正在加载文件内容
                    String loadThink = "📂 正在检索文件内容，请稍等...";
                    sink.tryEmitNext(createThinkingResponse(loadThink));
                }

                try {
                    Object result = callback.call(argsJson);
                    ToolResponseMessage.ToolResponse tr = new ToolResponseMessage.ToolResponse(
                            tc.id(), toolName, result.toString());
                    messages.add(ToolResponseMessage.builder()
                            .responses(List.of(tr))
                            .build());

                    // 记录使用的工具
                    recordUsedTool(toolName);

                } catch (Exception ex) {
                    addErrorToolResponse(messages, tc, "工具执行失败：" + ex.getMessage());
                } finally {
                    completeToolCall(completedCount, totalToolCalls, onComplete);
                }
            });
        }
    }

    private void completeToolCall(AtomicInteger completedCount, int total, Runnable onComplete) {
        int current = completedCount.incrementAndGet();
        if (current >= total) {
            onComplete.run();
        }
    }

    private void addErrorToolResponse(List<Message> messages, AssistantMessage.ToolCall toolCall, String errMsg) {
        ToolResponseMessage.ToolResponse tr = new ToolResponseMessage.ToolResponse(
                toolCall.id(),
                toolCall.name(),
                "{ \"error\": \"" + errMsg + "\" }"
        );

        messages.add(ToolResponseMessage.builder()
                .responses(List.of(tr))
                .build());
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
        private String systemPrompt = "";
        private int maxRounds;
        private ChatMemory chatMemory;
        private AiSessionService sessionService;
        private AgentTaskManager taskManager;

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

        public Builder systemPrompt(String systemPrompt) {
            this.systemPrompt = systemPrompt;
            return this;
        }

        public Builder maxRounds(int maxRounds) {
            this.maxRounds = maxRounds;
            return this;
        }

        public Builder chatMemory(ChatMemory chatMemory) {
            this.chatMemory = chatMemory;
            return this;
        }

        public Builder sessionService(AiSessionService sessionService) {
            this.sessionService = sessionService;
            return this;
        }

        public Builder taskManager(AgentTaskManager taskManager) {
            this.taskManager = taskManager;
            return this;
        }

        public FileReactAgent build() {
            if (chatModel == null) {
                throw new IllegalArgumentException("chatModel 不能为空！");
            }
            return new FileReactAgent(name, chatModel, tools, systemPrompt, maxRounds, chatMemory, sessionService, taskManager);
        }
    }
}
