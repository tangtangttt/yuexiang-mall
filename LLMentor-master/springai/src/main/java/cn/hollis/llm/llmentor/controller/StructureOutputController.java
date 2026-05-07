package cn.hollis.llm.llmentor.controller;

import cn.hollis.llm.llmentor.model.Book;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.converter.MapOutputConverter;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/structure")
public class StructureOutputController implements InitializingBean {

    @Autowired
    private ChatModel dashScopeChatModel;

    private ChatClient chatClient;

    @RequestMapping("/call")
    public String call() {
        PromptTemplate promptTemplate = PromptTemplate.builder().template("请给我推荐几本心理学有关的书，输出格式：{format}").build();

        BeanOutputConverter<Book> converter = new BeanOutputConverter<Book>(Book.class);

        String resp = chatClient.prompt(promptTemplate.create(Map.of("format", converter.getFormat())))
                .call().chatResponse().getResult().getOutput().getText();

        Book book = converter.convert(resp);

        System.out.println(book.toString());

        return book.name() + " " + book.author() + " " + book.desc() + " " + book.price() + " " + book.publisher();
    }

    @RequestMapping("/convert")
    public String convert() {
        Book book = chatClient.prompt("请给我推荐几本心理学有关的书")
                .call().entity(Book.class);
        System.out.println(book.toString());
        return book.name() + " 、 " + book.author() + " 、 " + book.desc() + " 、 " + book.price() + " 、 " + book.publisher();
    }


    @RequestMapping("/convertList")
    public String convertList() {
        List<Book> book = chatClient.prompt("请给我推荐几本心理学有关的书")
                .call().entity(new ParameterizedTypeReference<List<Book>>() {
                });

        System.out.println(book.toString());
        return book.toString();
    }

    @RequestMapping("/convertMap")
    public String convertMap() {
        Map<String, Object> book = chatClient.prompt("请给我推荐几本心理学有关的书，书的内容包括书名、作者、价格、上市时间等信息，以书名作为key，书的信息作为value")
                .call().entity(new MapOutputConverter());

        System.out.println(book.toString());
        return book.toString();
    }


    @Override
    public void afterPropertiesSet() throws Exception {
        chatClient = ChatClient.builder(dashScopeChatModel)
                // 实现 Logger 的 Advisor
                .defaultAdvisors(
                        new SimpleLoggerAdvisor()
                )
                // 设置 ChatClient 中 ChatModel 的 Options 参数
                .defaultOptions(
                        DashScopeChatOptions.builder()
                                .temperature(0.7)
                                .build()
                )
                .build();
    }
}
