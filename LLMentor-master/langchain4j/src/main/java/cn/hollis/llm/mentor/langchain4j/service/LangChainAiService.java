package cn.hollis.llm.mentor.langchain4j.service;

import cn.hollis.llm.mentor.langchain4j.Book;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.spring.AiService;
import reactor.core.publisher.Flux;


@AiService
public interface LangChainAiService {

    String chat(String userMessage);

    Flux<String> chatStream(String userMessage);

    @SystemMessage("你是一个毒舌博主，擅长怼人")
    @UserMessage("针对用户的内容：{{topic}}，先复述一遍他的问题，然后再回答")
    Flux<String> chatTemplate(String topic);

    @UserMessage("请帮我推荐1本java相关的书")
    @SystemMessage("你是一个专业的图书推荐人员")
    Book getBooks();
}
