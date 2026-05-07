package cn.hollis.llm.mentor.mcp.service;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
@Slf4j
public class RetrySSEMcpService {

    @Autowired
    private OpenAiChatModel chatModel;

    private ChatClient chatClient;

    private McpSyncClient sseClient;

    // 是否正在重试 initialize（保证唯一性）
    private final AtomicBoolean retrying = new AtomicBoolean(false);

    // initialize 重试线程
    private final ExecutorService retryExecutor = Executors.newSingleThreadExecutor();

    @PostConstruct
    public void init() {
        log.info("Initializing SSE MCP Client...");

        // 初始化 SSE Client
        this.sseClient = buildClient();

        try {
            this.sseClient.initialize();
            log.info("SSE MCP client initialized.");
        } catch (Exception e) {
            log.error("Initial SSE initialize failed, will rely on retry thread.", e);
            // 启动重试线程
            startRetryInitialize();
        }

        // 初始化 toolcallback
        SyncMcpToolCallbackProvider provider = SyncMcpToolCallbackProvider.builder()
                .mcpClients(List.of(this.sseClient))
                .build();

        ToolCallback[] callbacks = provider.getToolCallbacks();

        this.chatClient = ChatClient.builder(chatModel)
                .defaultToolCallbacks(callbacks)
                .defaultTools()
                .build();
    }

    private McpSyncClient buildClient() {
        HttpClientSseClientTransport transport = HttpClientSseClientTransport
                .builder("http://127.0.0.1:8003")
                .sseEndpoint("/sse")
                .build();

        return McpClient.sync(transport)
                .clientInfo(new io.modelcontextprotocol.spec.McpSchema.Implementation("sse-client", "1.0"))
                .requestTimeout(Duration.ofSeconds(10))
                .build();
    }

    /**
     * 定时任务：每 5 秒 ping 一次 SSE
     * ping 不通则触发 initialize 重试线程
     */
    @Scheduled(fixedDelay = 5000)
    public void pingSse() {
        log.info("SSE MCP ping...");
        if (sseClient == null) {
            log.warn("SSE client not initialized yet.");
            startRetryInitialize();
            return;
        }

        try {
            sseClient.ping();
            log.debug("SSE MCP ping OK.");
        } catch (Exception e) {
            log.error("SSE MCP ping failed: {}", e.getMessage());
            startRetryInitialize();
        }
    }

    /**
     * 启动 initialize 重试线程
     */
    private void startRetryInitialize() {
        // 保证只启动一个重试线程
        if (!retrying.compareAndSet(false, true)) {
            return;
        }

        retryExecutor.submit(() -> {
            log.warn("Start retrying SSE MCP initialize...");

            while (true) {
                try {
                    // 重建 sseClient
                    this.sseClient = buildClient();
                    this.sseClient.initialize();
                    log.info("SSE MCP re-initialized successfully.");

                    // chatclient 也同样需要重建
                    SyncMcpToolCallbackProvider provider = SyncMcpToolCallbackProvider.builder()
                            .mcpClients(List.of(this.sseClient))
                            .build();

                    ToolCallback[] callbacks = provider.getToolCallbacks();

                    this.chatClient = ChatClient.builder(chatModel)
                            .defaultToolCallbacks(callbacks)
                            .defaultTools()
                            .build();

                    retrying.set(false);
                    return;
                } catch (Exception e) {
                    log.warn("Retry initialize failed, will retry in 10s. Reason: {}", e.getMessage());
                }

                try {
                    Thread.sleep(10000);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        });

    }

    public String chat(String userMessage) {
        return chatClient.prompt()
                .user(userMessage)
                .call()
                .content();
    }
}
