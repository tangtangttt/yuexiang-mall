package cn.hollis.llm.mentor.langchain4j.controller;

import cn.hollis.llm.mentor.langchain4j.service.LangChainAiService;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.parser.apache.tika.ApacheTikaDocumentParser;
import dev.langchain4j.data.document.splitter.DocumentByParagraphSplitter;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.input.PromptTemplate;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.rag.DefaultRetrievalAugmentor;
import dev.langchain4j.rag.content.injector.DefaultContentInjector;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

import static dev.langchain4j.data.document.loader.FileSystemDocumentLoader.loadDocument;

@RestController
@RequestMapping("/langchain4j/rag")
public class LangChainRagController {

    @Value("${langchain4j.open-ai.chat-model.api-key}")
    private String openAiApiKey;

    @Autowired
    OpenAiChatModel chatModel;

    @RequestMapping("/retrieve")
    public String retrieve(HttpServletResponse response, String query, String filePath) {
        response.setCharacterEncoding("UTF-8");

        //1.加载文档
        Document document = loadDocument(filePath, new ApacheTikaDocumentParser());


        DocumentByParagraphSplitter splitter = new DocumentByParagraphSplitter(
                300,
                50
        );

        //2.分割文档
        List<TextSegment> textSegments = splitter.split(document);

        OpenAiEmbeddingModel embeddingModel = OpenAiEmbeddingModel.builder()
                .modelName("text-embedding-v4")  // 阿里云 DashScope 的 embedding 模型名称
                .dimensions(768)  // text-embedding-v4 支持 768 维度
                .baseUrl("https://dashscope.aliyuncs.com/compatible-mode/v1")
                .apiKey("改成你自己的key").build();

        //3.生成embedding
        List<Embedding> allEmbeddings = new ArrayList<>();
        for (int i = 0; i < textSegments.size(); i = i + 9) {
            List<TextSegment> segmentList = textSegments.subList(i, Math.min(i + 9, textSegments.size()));
            List<Embedding> embeddings = embeddingModel.embedAll(segmentList).content();
            allEmbeddings.addAll(embeddings);
        }

        //4.向量存储
        InMemoryEmbeddingStore<TextSegment> embeddingStore = new InMemoryEmbeddingStore<>();
        embeddingStore.addAll(allEmbeddings, textSegments);

        //5.构建上下文融合器
        DefaultContentInjector contentInjector = new DefaultContentInjector(new PromptTemplate("""
                 ## 角色定位
                 你是一位专业的RAG问答助手。请根据提供的上下文信息，详细、准确地回答用户的问题。如果参考文档没有内容，请务必不要胡编乱造，请直接说明"没有找到相关信息"。
                
                 ## 任务要求：
                 1. 请基于以下提供的参考文档内容，回答用户的问题。
                 2. 如果参考文档中没有相关信息，请直接说明"没有找到相关信息"，不要编造内容。
                 3. 如果有了参考文档内容，请务必尽量回答问题。有可能用户的输入比较随意，你可以先尝试回答用户的问题，猜测他的实际需求，先给出回复，你需要尽量去贴合用户的问题需求。
                
                 ## 格式要求：
                 1. 你的所有回答必须使用Markdown格式进行排版。
                 2. 上下文信息中包含了图片描述标签，格式为：`<image src="URL" description="多模态描述"></image>`。
                 3. 如果图片与用户提问高度相关，请将此标签转换为标准的Markdown图片格式 `![图片](URL)`。
                 4. 仅在必要时包含图片，请注意千万不要输出重复的内容和图片，图片确保最终生成的URL不要重复。
                
                 ## 参考文档:
                {{contents}}
                
                 ## 用户问题:
                 {{userMessage}}
                
                 注意：如果参考文档下面的内容为空，请直接回答“没有找到相关信息”。
                """));

        //6.构建检索增强器
        DefaultRetrievalAugmentor retrievalAugmentor = DefaultRetrievalAugmentor.builder()
                .contentRetriever(EmbeddingStoreContentRetriever.builder()
                        .embeddingStore(embeddingStore)
                        .embeddingModel(embeddingModel)
                        .maxResults(5)
                        .minScore(0.7)
                        .build())
                .contentInjector(contentInjector)
                .build();


        //7.构建最终的AI服务
        LangChainAiService langChainAiService = AiServices.builder(LangChainAiService.class)
                .chatModel(chatModel)
                .retrievalAugmentor(retrievalAugmentor)
                .chatMemory(MessageWindowChatMemory.withMaxMessages(10))
                .build();

        //8.调用AI服务
        return langChainAiService.chat(query);
    }

    @RequestMapping("/retrieve1")
    public String retrieve1(HttpServletResponse response, String query, String filePath) {
        response.setCharacterEncoding("UTF-8");

        // 1. 配置 Embedding 模型
        OpenAiEmbeddingModel embeddingModel = OpenAiEmbeddingModel.builder()
                .modelName("text-embedding-v3")
                .dimensions(768)
                .baseUrl("https://dashscope.aliyuncs.com/compatible-mode/v1")
                .maxSegmentsPerBatch(9)
                .apiKey("sk-0227f9a97bef4f2c8fc899d82831aa25")
                .build();

        // 2. 加载文档并生成 Embeddings
        InMemoryEmbeddingStore<TextSegment> embeddingStore = new InMemoryEmbeddingStore<>();
        EmbeddingStoreIngestor.builder()
                .documentSplitter(DocumentSplitters.recursive(300, 50))
                .embeddingModel(embeddingModel)
                .embeddingStore(embeddingStore)
                .build()
                .ingest(loadDocument(filePath, new ApacheTikaDocumentParser()));

        // 3. 构建 RAG 增强器（使用链式调用）
        DefaultRetrievalAugmentor retrievalAugmentor = DefaultRetrievalAugmentor.builder()
                .contentRetriever(EmbeddingStoreContentRetriever.builder()
                        .embeddingStore(embeddingStore)
                        .embeddingModel(embeddingModel)
                        .maxResults(5)
                        .minScore(0.7)
                        .build())
                .contentInjector(new DefaultContentInjector(new PromptTemplate("""
                          ## 角色定位
                         你是一位专业的RAG问答助手。请根据提供的上下文信息，详细、准确地回答用户的问题。如果参考文档没有内容，请务必不要胡编乱造，请直接说明"没有找到相关信息"。
                        
                         ## 任务要求：
                         1. 请基于以下提供的参考文档内容，回答用户的问题。
                         2. 如果参考文档中没有相关信息，请直接说明"没有找到相关信息"，不要编造内容。
                         3. 如果有了参考文档内容，请务必尽量回答问题。有可能用户的输入比较随意，你可以先尝试回答用户的问题，猜测他的实际需求，先给出回复，你需要尽量去贴合用户的问题需求。
                        
                         ## 格式要求：
                         1. 你的所有回答必须使用Markdown格式进行排版。
                         2. 上下文信息中包含了图片描述标签，格式为：`<image src="URL" description="多模态描述"></image>`。
                         3. 如果图片与用户提问高度相关，请将此标签转换为标准的Markdown图片格式 `![图片](URL)`。
                         4. 仅在必要时包含图片，请注意千万不要输出重复的内容和图片，图片确保最终生成的URL不要重复。
                        
                         ## 参考文档:
                        {{contents}}
                        
                         ## 用户问题:
                         {{userMessage}}
                        
                         注意：如果参考文档下面的内容为空，请直接回答“没有找到相关信息”。
                        """)))
                .build();

        // 4. 构建 AI 服务并返回结果
        return AiServices.builder(LangChainAiService.class)
                .chatModel(chatModel)
                .retrievalAugmentor(retrievalAugmentor)
                .chatMemory(MessageWindowChatMemory.withMaxMessages(10))
                .build()
                .chat(query);
    }

}
