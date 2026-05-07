package cn.hollis.llm.mentor.agent.agent;

import cn.hollis.llm.mentor.agent.advisor.ReflectionAdvisor;
import cn.hollis.llm.mentor.agent.config.ChatModelConfig;
import cn.hollis.llm.mentor.agent.tools.SearchService;
import cn.hollis.llm.mentor.agent.tools.WeatherService;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ReflectionAgent {

    private final SimpleReactAgent delegate;

    private ReflectionAgent(SimpleReactAgent delegate) {
        this.delegate = delegate;
    }

    public String call(String question) {
        return delegate.call(question);
    }

    public String call(String conversationId, String question) {
        return delegate.call(conversationId, question);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private String name = "reflection-react-agent";
        private ChatModel chatModel;
        private List<ToolCallback> tools = new ArrayList<>();
        private int maxRounds;
        private String systemPrompt = "";
        private List<Advisor> advisors = new ArrayList<>();
        private int maxReflectionRounds = 1;

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

        public Builder advisors(Advisor... advisors) {
            this.advisors.addAll(Arrays.asList(advisors));
            return this;
        }

        public Builder systemPrompt(String systemPrompt) {
            this.systemPrompt = systemPrompt;
            return this;
        }

        public Builder maxReflectionRounds(int maxReflectionRounds) {
            this.maxReflectionRounds = maxReflectionRounds;
            return this;
        }

        public Builder maxRounds(int maxRounds) {
            this.maxRounds = maxRounds;
            return this;
        }

        public ReflectionAgent build() {

            if (chatModel == null) {
                throw new IllegalArgumentException("chatModel 不能为空");
            }

            ReflectionAdvisor reflectionAdvisor = new ReflectionAdvisor(chatModel);

            List<Advisor> finalAdvisors = new ArrayList<>(advisors);
            finalAdvisors.add(reflectionAdvisor);

            ChatMemory chatMemory = MessageWindowChatMemory.builder().maxMessages(20).build();

            SimpleReactAgent reactAgent = SimpleReactAgent.builder()
                    .name(name)
                    .chatModel(chatModel)
                    .tools(tools)
                    .maxRounds(maxRounds)
                    .systemPrompt(systemPrompt)
                    .chatMemory(chatMemory)
                    .maxReflectionRounds(maxReflectionRounds)
                    .advisors(finalAdvisors)
                    .build();

            return new ReflectionAgent(reactAgent);
        }
    }

    public static void main(String[] args) {
        ChatModel chatModel = ChatModelConfig.getChatModel();

        ToolCallback[] toolCallbacks = ToolCallbacks.from(new WeatherService(), new SearchService());

        ReflectionAgent agent = ReflectionAgent.builder()
                .name("ReflectionAgent")
                .chatModel(chatModel)
                .maxReflectionRounds(2)
                .maxRounds(-1)
                .tools(toolCallbacks)
                .systemPrompt("你是专业的研究分析助手！")
                .build();

        String question = """
                请你根据北京今天的天气、未来七天的天气趋势、以及上海今天的天气，并搜索北京天气的预警情况，生成一份不少于 200 字的综合分析报告。
                """;

        System.out.println(agent.call(question));
    }
}
