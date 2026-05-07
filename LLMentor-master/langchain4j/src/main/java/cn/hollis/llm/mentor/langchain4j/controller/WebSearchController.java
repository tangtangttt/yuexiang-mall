package cn.hollis.llm.mentor.langchain4j.controller;

import cn.hollis.llm.mentor.langchain4j.service.LangChainAiService;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.rag.DefaultRetrievalAugmentor;
import dev.langchain4j.rag.content.retriever.WebSearchContentRetriever;
import dev.langchain4j.rag.query.transformer.CompressingQueryTransformer;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.web.search.tavily.TavilyWebSearchEngine;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/websearch")
public class WebSearchController {

    @Autowired
    OpenAiChatModel chatModel;

    @GetMapping("/search")
    public String webSearch() {
        // 1. 配置搜索引擎
        TavilyWebSearchEngine searchEngine = TavilyWebSearchEngine.builder()
                .apiKey("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")
                .includeAnswer(true)
                .searchDepth("advanced")
                .build();

        // 2. 配置 Web 搜索检索器
        WebSearchContentRetriever webRetriever = WebSearchContentRetriever.builder()
                .webSearchEngine(searchEngine)
                .maxResults(5)
                .build();

        CompressingQueryTransformer queryTransformer = CompressingQueryTransformer.builder()
                .chatModel(chatModel)
                .build();

        // 3. 配置 RetrievalAugmentor
        DefaultRetrievalAugmentor augmentor = DefaultRetrievalAugmentor.builder()
                .contentRetriever(webRetriever)
                .queryTransformer(queryTransformer)
                .build();

        LangChainAiService assistant = AiServices.builder(LangChainAiService.class)
                .chatModel(chatModel)
                .retrievalAugmentor(augmentor)
                .build();

        // 6. 使用 - 获取实时信息
        String answer = assistant.chat("2025年人工智能领域有哪些重大突破？");
        return answer;
    }

}