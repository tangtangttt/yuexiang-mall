package cn.hollis.llm.mentor.rag.controller;

import cn.hollis.llm.mentor.rag.cleaner.DocumentCleaner;
import cn.hollis.llm.mentor.rag.embedding.EmbeddingService;
import cn.hollis.llm.mentor.rag.reader.DocumentReaderFactory;
import cn.hollis.llm.mentor.rag.splitter.OverlapParagraphTextSplitter;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/rag/embedding")
public class RagEmbeddingController {

    @Autowired
    private EmbeddingModel embeddingModel;

    @RequestMapping("/test")
    public String test() {
        for (float i : embeddingModel.embed("test")) {
            System.out.println(i);
        }
        return "success";
    }

    @Autowired
    private DocumentReaderFactory documentReaderFactory;

    @Autowired
    private EmbeddingService embeddingService;

    @RequestMapping("embed")
    public String embed(String filePath) {

        List<Document> documents;
        try {
            documents = documentReaderFactory.read(new File(filePath));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        //清洗并分段
        List<Document> allChunkedDocuments = DocumentCleaner.cleanDocuments(documents).stream()
                .flatMap(document -> {
                    OverlapParagraphTextSplitter splitter = new OverlapParagraphTextSplitter(1000, 50);
                    return splitter.split(document).stream();
                })
                .collect(Collectors.toList());

        //向量化并存储
        embeddingService.embedAndStore(DocumentCleaner.cleanDocuments(allChunkedDocuments));

        return "success";
    }
}
