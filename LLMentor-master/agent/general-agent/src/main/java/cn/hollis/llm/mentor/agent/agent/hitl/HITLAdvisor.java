package cn.hollis.llm.mentor.agent.agent.hitl;

import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.messages.AssistantMessage;

import java.util.*;

public class HITLAdvisor implements CallAdvisor {

    public static final String HITL_REQUIRED = "hitl.required";
    public static final String HITL_PENDING_TOOLS = "hitl.pending.tools";
    public static final String HITL_STATE_KEY = "hitl.state";

    private final Set<String> interceptToolNames;

    public HITLAdvisor(Set<String> interceptToolNames) {
        this.interceptToolNames = interceptToolNames;
    }

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest chatClientRequest, CallAdvisorChain callAdvisorChain) {
        ChatClientResponse response = callAdvisorChain.nextCall(chatClientRequest);
        if (!response.chatResponse().hasToolCalls()) {
            return response;
        }

        List<PendingToolCall> pending = new ArrayList<>();

        for (AssistantMessage.ToolCall tc : response.chatResponse().getResult().getOutput().getToolCalls()) {

            if (!interceptToolNames.contains(tc.name())) {
                continue;
            }

            pending.add(new PendingToolCall(tc.id(), tc.name(), tc.arguments(), null, "该工具需要用户手动确认"));
        }

        if (pending.isEmpty()) {
            return response;
        }

        response.context().put(HITL_REQUIRED, true);
        response.context().put(HITL_PENDING_TOOLS, pending);

        return response;
    }

    @Override
    public String getName() {
        return "HITLAdvisor";
    }

    @Override
    public int getOrder() {
        return 0;
    }
}
