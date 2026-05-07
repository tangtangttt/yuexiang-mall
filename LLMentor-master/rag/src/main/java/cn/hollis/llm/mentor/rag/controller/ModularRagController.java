package cn.hollis.llm.mentor.rag.controller;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.rag.generation.augmentation.ContextualQueryAugmenter;
import org.springframework.ai.rag.generation.augmentation.QueryAugmenter;
import org.springframework.ai.rag.preretrieval.query.expansion.MultiQueryExpander;
import org.springframework.ai.rag.preretrieval.query.expansion.QueryExpander;
import org.springframework.ai.rag.preretrieval.query.transformation.RewriteQueryTransformer;
import org.springframework.ai.rag.retrieval.search.DocumentRetriever;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/rag/modular")
public class
ModularRagController implements InitializingBean {

    @Autowired
    ChatModel chatModel;

    ChatClient chatClient;

    @Autowired
    private VectorStore vectorStore;


    @GetMapping("/retriever")
    public String retriever(String query) {

        DocumentRetriever retriever = VectorStoreDocumentRetriever.builder()
                .vectorStore(vectorStore)          // 必需：绑定向量存储
                .topK(5)                             // 返回最相似的 5 个文档
                .similarityThreshold(0.6)            // 相似度低于 0.6 的过滤掉
                .build();

        RewriteQueryTransformer queryTransformer = RewriteQueryTransformer.builder()
                .chatClientBuilder(ChatClient.builder(chatModel))
                .promptTemplate(new PromptTemplate("""
                        Given a user query, rewrite it to provide better results when querying a {target}.
                        
                        Remove any irrelevant information, and ensure the query is concise and specific.
                        
                        如果有表述不清的内容，或者错别字，请修正，如"华子"，修改为"华为"
                        
                        Original query:
                        {query}
                        
                        Rewritten query:
                        """))
                .build();


        QueryExpander queryExpander = MultiQueryExpander.builder()
                .chatClientBuilder(ChatClient.builder(chatModel))
                .numberOfQueries(3)
                .includeOriginal(true)
                .build();

        QueryAugmenter queryAugmenter = ContextualQueryAugmenter.builder()
                .allowEmptyContext(true)
                .emptyContextPromptTemplate(new PromptTemplate("请回答以下用户问题"))
                .build();

        RetrievalAugmentationAdvisor advisor = RetrievalAugmentationAdvisor.builder()
                .documentRetriever(retriever)
                .queryTransformers(queryTransformer)
                .queryExpander(queryExpander)
                .queryAugmenter(queryAugmenter)
                .build();

        return chatClient.prompt(query).advisors(advisor).call().content();
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        chatClient = ChatClient.builder(chatModel).defaultAdvisors(new SimpleLoggerAdvisor()).build();
    }
}
