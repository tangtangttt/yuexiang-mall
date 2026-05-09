package com.yuex.mobile.customer.record;

import lombok.Data;
import org.springframework.ai.chat.messages.AssistantMessage;

import java.util.ArrayList;
import java.util.List;

import static java.util.Collections.synchronizedList;

@Data
public class RoundState {
    public RoundMode mode = RoundMode.UNKNOWN;
    public StringBuilder textBuffer = new StringBuilder();
    public List<AssistantMessage.ToolCall> toolCalls = synchronizedList(new ArrayList<>());
}
