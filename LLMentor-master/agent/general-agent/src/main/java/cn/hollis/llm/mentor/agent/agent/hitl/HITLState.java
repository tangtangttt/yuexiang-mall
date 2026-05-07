package cn.hollis.llm.mentor.agent.agent.hitl;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class HITLState {

    private final Set<String> consumedToolCallIds = ConcurrentHashMap.newKeySet();

    public HITLState() {
    }

    public boolean isConsumed(String toolCallId) {
        return consumedToolCallIds.contains(toolCallId);
    }

    public void markConsumed(String toolCallId) {
        consumedToolCallIds.add(toolCallId);
    }
}
