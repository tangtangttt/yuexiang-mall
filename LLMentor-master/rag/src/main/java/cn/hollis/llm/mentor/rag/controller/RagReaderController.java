package cn.hollis.llm.mentor.rag.controller;

import cn.hollis.llm.mentor.rag.cleaner.DocumentCleaner;
import cn.hollis.llm.mentor.rag.reader.DocumentReaderFactory;
import cn.hollis.llm.mentor.rag.splitter.OverlapParagraphTextSplitter;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.File;
import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/rag")
public class RagReaderController {

    @Autowired
    private DocumentReaderFactory documentReaderFactory;

    @RequestMapping("/read")
    public String read(String filePath) {
        List<Document> documents;
        try {
            documents = DocumentCleaner.cleanDocuments(documentReaderFactory.read(new File(filePath)));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        System.out.println(documents.size());
        StringBuffer sb = new StringBuffer();
        for (Document document : documents) {
            sb.append(document.getText());
            System.out.println(document.getText());
            System.out.println(document.getMetadata());
            System.out.println("========");
            sb.append("========================");
        }
        return sb.toString();
    }


    @RequestMapping("/chunker")
    public String chunker(String filePath) {
        List<Document> documents;
        try {
            documents = DocumentCleaner.cleanDocuments(documentReaderFactory.read(new File(filePath)));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        for (Document document : documents) {
            System.out.println("bofore chunk : " + document.getText());
            System.out.println("");
            OverlapParagraphTextSplitter tokenTextSplitter = new OverlapParagraphTextSplitter(
                    100,
                    5);

            List<Document> chunkedDocuments = tokenTextSplitter.split(document);

            for (Document chunkedDocument : chunkedDocuments) {
                System.out.println("after chunk : " + chunkedDocument.getText());
                System.out.println("");
            }
            System.out.println("==============");
        }
        return "success";
    }
}
