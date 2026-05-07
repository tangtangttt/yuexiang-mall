package cn.hollis.llm.mentor.langchain4j.controller;

import cn.hollis.llm.mentor.langchain4j.Book;
import cn.hollis.llm.mentor.langchain4j.chatmemory.RedisChatMemoryStore;
import cn.hollis.llm.mentor.langchain4j.service.LangChainAiService;
import cn.hollis.llm.mentor.langchain4j.service.LangChainMemoryAiService;
import cn.hollis.llm.mentor.langchain4j.tools.TemperatureTools;
import com.alibaba.fastjson2.JSON;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.service.AiServices;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@RequestMapping("/langchain/high")
@RestController
public class LangChainHighLevelController implements InitializingBean {

    @Autowired
    private LangChainAiService aiService;

    @RequestMapping("/chat")
    public String chat(HttpServletResponse response) {
        response.setCharacterEncoding("UTF-8");
        return aiService.chat("日本都有哪些美食？");
    }

    @RequestMapping("/streamChat")
    public Flux<String> streamChat(HttpServletResponse response) {
        response.setCharacterEncoding("UTF-8");
        return aiService.chatStream("日本都有哪些美食？");
    }

    @RequestMapping("/chatTemplate")
    public Flux<String> chatTemplate(HttpServletResponse response) {
        response.setCharacterEncoding("UTF-8");
        return aiService.chatTemplate("我饿了？");
    }

    @RequestMapping("/structure")
    public String structure(HttpServletResponse response) {
        response.setCharacterEncoding("UTF-8");
        Book books = aiService.getBooks();
        return JSON.toJSONString(books);
    }

    @Autowired
    OpenAiChatModel chatModel;


    private LangChainMemoryAiService langChainMemoryAiService;

    @RequestMapping("/memoryChat")
    public String memoryChat(HttpServletResponse response, String msg, String memoryId) {
        response.setCharacterEncoding("UTF-8");
        return langChainMemoryAiService.chatMemory(memoryId, msg);
    }

    @RequestMapping("/toolCalling")
    public String toolCalling(HttpServletResponse response, String msg) {
        response.setCharacterEncoding("UTF-8");

        LangChainAiService langChainAiService1 = AiServices.builder(LangChainAiService.class)
                .tools(new TemperatureTools())
                .chatModel(chatModel)
                .build();

        return langChainAiService1.chat(msg);
    }

    @Autowired
    private RedisChatMemoryStore redisChatMemoryStore;

    @Override
    public void afterPropertiesSet() throws Exception {
        langChainMemoryAiService = AiServices.builder(LangChainMemoryAiService.class)
                .chatModel(chatModel)
                .chatMemoryProvider(memoryId -> MessageWindowChatMemory.withMaxMessages(10))
//                .chatMemoryProvider(memoryId -> MessageWindowChatMemory.builder().id(memoryId).maxMessages(10).chatMemoryStore(redisChatMemoryStore).build())
                .build();
    }
}
