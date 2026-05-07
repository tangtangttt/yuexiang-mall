package cn.hollis.llm.mentor.agent.entity;

import cn.hollis.llm.mentor.agent.entity.record.PlanRoundState;
import cn.hollis.llm.mentor.agent.entity.record.TaskResult;
import lombok.Data;
import org.springframework.ai.chat.messages.Message;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Data
public class OverAllState {

    private final String conversationId;
    private final String question;
    private final List<Message> messages = new ArrayList<>();
    private final List<PlanRoundState> rounds = new ArrayList<>();
    private int round = 0;
    // 优化后的研究主题（在研究主题生成环节设置）
    private String refinedResearchTopic;

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

    public void addRound(PlanRoundState r) {
        rounds.add(r);
    }

    public int currentChars() {
        return messages.stream()
                .mapToInt(m -> m.getText() == null ? 0 : m.getText().length())
                .sum();
    }

    private String renderMessages() {
        return this.getMessages().stream()
                .map(m -> m.getText())
                .collect(Collectors.joining("\n\n"));
    }


    public void clearMessages() {
        messages.clear();
    }

    /**
     * 提取所有轮次的工具执行结果
     * 用于总结阶段生成报告
     */
    public String extractToolResults() {
        if (rounds == null || rounds.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        for (PlanRoundState round : rounds) {
            if (round.results() != null && !round.results().isEmpty()) {
                for (var entry : round.results().entrySet()) {
                    TaskResult result = entry.getValue();
                    if (result != null && result.success() && result.output() != null) {
                        sb.append(String.format("【任务 %s 执行结果】\n%s\n\n", entry.getKey(), result.output()));
                    }
                }
            }
        }
        return sb.toString();
    }
}
