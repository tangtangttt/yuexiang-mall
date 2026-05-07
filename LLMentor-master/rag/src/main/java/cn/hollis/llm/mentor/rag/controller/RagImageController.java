package cn.hollis.llm.mentor.rag.controller;

import cn.hollis.llm.mentor.rag.embedding.EmbeddingService;
import cn.hollis.llm.mentor.rag.reader.PdfMultimodalProcessor;
import cn.hollis.llm.mentor.rag.splitter.ModalTextSplitter;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.alibaba.cloud.ai.dashscope.chat.MessageFormat;
import com.alibaba.cloud.ai.dashscope.common.DashScopeApiConstants;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.content.Media;
import org.springframework.ai.document.Document;
import org.springframework.ai.model.SimpleApiKey;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.rag.generation.augmentation.ContextualQueryAugmenter;
import org.springframework.ai.rag.generation.augmentation.QueryAugmenter;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.List;

@RestController
@RequestMapping("/rag/image")
public class RagImageController {

    @Autowired
    private ChatModel chatModel;

    @RequestMapping("/callWithSpringAiAlibaba")
    public String callWithSpringAiAlibaba() throws URISyntaxException, MalformedURLException {
        List<Media> mediaList = List.of(new Media(MimeTypeUtils.IMAGE_PNG, new URI("https://cdn.nlark.com/yuque/0/2025/png/5378072/1762350625634-664f1db7-e1c9-4daa-ab8e-81b6b7da5a68.png").toURL().toURI()));

        var userMessage = UserMessage.builder().text("请详细的描述一下你看到的这个图片?").media(mediaList).build();

        return chatModel.call(new Prompt(userMessage, DashScopeChatOptions.builder().withModel("qwen3-vl-plus").withMultiModel(true).build())).getResult().getOutput().getText();
    }

    @RequestMapping("/callWithOpenAI")
    public String callWithOpenAI() throws URISyntaxException, MalformedURLException {

        OpenAiChatOptions options = OpenAiChatOptions.builder().temperature(0.2d).model("qwen3-vl-plus").build();
        OpenAiChatModel multimodalChatModel = OpenAiChatModel.builder().openAiApi(OpenAiApi.builder().baseUrl("https://dashscope.aliyuncs.com/compatible-mode/").apiKey(new SimpleApiKey("sk-8ef405c4686e456e91f6698272253126")).build()).defaultOptions(options).build();

        List<Media> mediaList = List.of(new Media(MimeTypeUtils.IMAGE_PNG, new URI("https://cdn.nlark.com/yuque/0/2025/png/5378072/1762350625634-664f1db7-e1c9-4daa-ab8e-81b6b7da5a68.png").toURL().toURI()));

        var userMessage = UserMessage.builder().text("请非常简要的描述一下你看到的这个图片?").media(mediaList).build();
        var response = multimodalChatModel.call(new Prompt(List.of(userMessage)));

        return response.getResult().getOutput().getText();
    }


    @RequestMapping("/callWithChatClient")
    public String callWithChatClient() throws URISyntaxException, MalformedURLException {
        List<Media> mediaList = List.of(new Media(MimeTypeUtils.IMAGE_PNG, new URI("https://dashscope.oss-cn-beijing.aliyuncs.com/images/dog_and_girl.jpeg").toURL().toURI()));
        UserMessage message = UserMessage.builder().text("请非常简要的描述一下你看到的这个图片?").media(mediaList).metadata(new HashMap<>()).build();
        message.getMetadata().put(DashScopeApiConstants.MESSAGE_FORMAT, MessageFormat.IMAGE);
        ChatClient chatClient = ChatClient.builder(chatModel).defaultOptions(OpenAiChatOptions.builder().model("qwen3-vl-plus").build()).build();
        ChatResponse response = chatClient.prompt(new Prompt(message, DashScopeChatOptions.builder().withModel("qwen3-vl-plus").withMultiModel(true).build())).call().chatResponse();
        return response.getResult().getOutput().getText();
    }

    @Autowired
    private PdfMultimodalProcessor processer;

    @RequestMapping("/process")
    public String process(String filePath) {
        try {
            return processer.processPdf(new File(filePath));
        } catch (Exception e) {
            e.printStackTrace();
            return "error";
        }
    }

    @Autowired
    private EmbeddingService embeddingService;

    @RequestMapping("/processFile")
    public String processFile(String filePath) throws Exception {

        //多模态的文件的处理（包括了文件中的图片转文字）
        String result = processer.processPdf(new File(filePath));

        //文档内容清晰，移除一些不必要的符号
//        List<Document> cleanedDocuments = DocumentCleaner.cleanDocuments(List.of(new Document(result)));

        //定义多模态分块器，对文档做分块
        ModalTextSplitter modalTextSplitter = new ModalTextSplitter(300, 20);

        List<Document> splittedDocuments = modalTextSplitter.split(List.of(new Document(result)));

        for (Document document : splittedDocuments) {
            System.out.println(document.getText());
            System.out.println("================");
        }

        //把分块后的结果做向量化并保存到向量数据库
        embeddingService.embedAndStore(splittedDocuments);

        return "success";
    }


    @Autowired
    private VectorStore vectorStore;

    @RequestMapping("chat")
    public String chat(String question) {

        VectorStoreDocumentRetriever retriever = VectorStoreDocumentRetriever.builder()
                .vectorStore(vectorStore)
                .topK(5)
                .similarityThreshold(0.3)
                .build();

        QueryAugmenter queryAugmenter = ContextualQueryAugmenter.builder()
                .allowEmptyContext(true)
                .promptTemplate(new PromptTemplate("""
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
                        {context}
                        
                        ## 用户问题:
                        {query}
                        
                        注意：如果参考文档下面的内容为空，请直接回答“没有找到相关信息”。
                        
                        """))
                .build();


        RetrievalAugmentationAdvisor retrievalAugmentationAdvisor = RetrievalAugmentationAdvisor.builder().documentRetriever(retriever).queryAugmenter(queryAugmenter).build();

        ChatClient chatClient = ChatClient.builder(chatModel).defaultAdvisors(retrievalAugmentationAdvisor).build();

        String result =  chatClient.prompt(new Prompt(question)).call().content();
        System.out.println(result);
        return result;
    }
}
