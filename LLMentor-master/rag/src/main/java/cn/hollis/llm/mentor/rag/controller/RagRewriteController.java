package cn.hollis.llm.mentor.rag.controller;

import cn.hollis.llm.mentor.rag.embedding.EmbeddingService;
import cn.hollis.llm.mentor.rag.rewriter.QueryRewriteService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/rag/rewrite")
@Slf4j
public class RagRewriteController {

    @Autowired
    private QueryRewriteService queryRewriteService;

    @Autowired
    private EmbeddingService embeddingService;

    @Autowired
    private ChatModel chatModel;

    @GetMapping("/chatWithQueryRewrite")
    public String chatWithQueryRewrite(@RequestParam("query") String query) {
        List<String> rewriteQuery = queryRewriteService
                .rewriteQuery(query);
        // set用作文档去重
        Set<Document> similarDocs = new LinkedHashSet<>();
        for (String q : rewriteQuery) {
            List<Document> docs = embeddingService.similaritySearch(q);
            if (docs != null && !docs.isEmpty()) {
                similarDocs.addAll(docs);
            }
        }
        // 2. 构建提示词模板
        String promptTemplate = """
                请基于以下提供的参考文档内容，回答用户的问题。
                
                参考文档:
                {documents}
                
                用户问题: {question}
                """;

        log.info("共检索到 {} 个相关文档块。", similarDocs.size());

        // 3. 处理检索到的文档内容
        String documentContent = similarDocs.stream()
                .map(Document::getText)
                .collect(Collectors.joining("\n\n=========文档分隔线===========\n\n"));

        log.info("查询到的文档信息：{}", documentContent);

        // 4. 填充模板参数
        Map<String, Object> params = new HashMap<>();
        params.put("documents", documentContent);
        params.put("question", query);
        PromptTemplate prompt = new PromptTemplate(promptTemplate);
        Prompt realPrompt = prompt.create(Map.of("documents", documentContent, "question", query));

        // 5. 调用大模型生成回答
        return chatModel.call(realPrompt).getResult().getOutput().getText();
    }
}
