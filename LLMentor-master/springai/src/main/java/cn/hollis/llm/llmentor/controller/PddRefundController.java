package cn.hollis.llm.llmentor.controller;

import cn.hollis.llm.llmentor.tools.OrderTools;
import cn.hollis.llm.llmentor.model.ChatStatus;
import cn.hollis.llm.llmentor.model.OrderChat;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.util.UUID;

import static org.springframework.ai.chat.memory.ChatMemory.CONVERSATION_ID;

@RestController
@RequestMapping("/pdd/refund")
public class PddRefundController {

    @Autowired
    private DashScopeChatModel chatModel;

    private ChatClient chatClient;

    @Autowired
    private OrderTools orderTools;

    @Value("classpath:templates/pdd_refund_system_prompt.pt")
    private Resource systemText;

    @GetMapping("/newChat")
    public OrderChat newChat(String userId, String orderId, HttpServletResponse httpServletResponse) {
        httpServletResponse.setCharacterEncoding("UTF-8");

        //模拟数据库创建一个chat的记录，获取到他的唯一id。
        String chatId = UUID.randomUUID().toString();

        return chatClient
                .prompt()
                .user(String.format("我要咨询订单相关的售后问题，我的用户id是%s,我的订单号是: %s ,本地的对话Id是 %s，当前状态是 %s", userId, orderId, chatId, ChatStatus.CHAT_START.name()))
                .advisors(spec -> spec.param(CONVERSATION_ID, chatId)
                        .param("chat_memory_retrieve_size", 100))
                .call().entity(OrderChat.class);
    }

    @GetMapping("/ask")
    public Flux<String> ask(String question, String chatId, HttpServletResponse httpServletResponse) {
        httpServletResponse.setCharacterEncoding("UTF-8");

        return chatClient
                .prompt()
                .user(question)
                .tools(orderTools)
                .advisors(spec -> spec.param(CONVERSATION_ID, chatId)
                        .param("chat_memory_retrieve_size", 100))
                .stream().content();
    }


    @Autowired
    private ChatMemory chatmemory;

    @PostConstruct
    public void init() {
        chatClient = ChatClient.builder(chatModel)
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatmemory).build(), new SimpleLoggerAdvisor())
                .defaultSystem(systemText)
                .build();
    }
}
