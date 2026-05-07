package cn.hollis.llm.mentor.function;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/function")
@Slf4j
@RequiredArgsConstructor
public class FunctionCallController {

    @Autowired
    private OpenAiChatModel chatModel;

    private ChatClient chatClient;

    @GetMapping("/chat")
    public String chat(@RequestParam("query") String query) {
        log.info("chat request => {}", query);

        return chatClient.prompt().toolNames("getTimeFunction").user(query).call().content();
//        return chatClient.prompt().tools(new TimeTools()).user(query).call().content();
    }

    @PostConstruct
    public void init() {
        ChatMemory chatMemory = MessageWindowChatMemory.builder().maxMessages(10).build();

        chatClient = ChatClient.builder(chatModel)
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .build();
    }
}
