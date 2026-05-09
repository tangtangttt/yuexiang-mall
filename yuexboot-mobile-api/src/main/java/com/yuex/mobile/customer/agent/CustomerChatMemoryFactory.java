package com.yuex.mobile.customer.agent;

import com.yuex.mobile.customer.service.CustomerServiceSessionService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Component;

/**
 * 从已落库的问答记录恢复多轮上下文（避免仅依赖进程内 Memory 导致重启丢上下文）
 */
@Component
@RequiredArgsConstructor
public class CustomerChatMemoryFactory {

    private final CustomerServiceSessionService sessionService;

    public ChatMemory buildFromDb(String sessionId, int maxMessages) {
        ChatMemory memory = MessageWindowChatMemory.builder()
                .maxMessages(Math.max(4, maxMessages))
                .build();
        int maxTurns = Math.max(1, maxMessages / 2);
        var history = sessionService.findAnsweredHistory(sessionId, maxTurns);
        for (var row : history) {
            if (row.getQuestion() != null) {
                memory.add(sessionId, new UserMessage(row.getQuestion()));
            }
            if (row.getAnswer() != null) {
                memory.add(sessionId, new AssistantMessage(row.getAnswer()));
            }
        }
        return memory;
    }
}
