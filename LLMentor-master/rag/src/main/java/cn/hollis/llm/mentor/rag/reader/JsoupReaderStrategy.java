package cn.hollis.llm.mentor.rag.reader;

import org.springframework.ai.document.Document;
import org.springframework.ai.reader.JsonReader;
import org.springframework.ai.reader.jsoup.JsoupDocumentReader;
import org.springframework.ai.reader.jsoup.config.JsoupDocumentReaderConfig;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.util.List;

@Service
public class JsoupReaderStrategy implements DocumentReaderStrategy {
    @Override
    public boolean supports(File file) {

        String name = file.getName().toLowerCase();
        return name.endsWith(".html") || name.endsWith(".htm");
    }

    @Override
    public List<Document> read(File file) throws IOException {
        Resource resource = new FileSystemResource(file);
        // 读取配置
        JsoupDocumentReaderConfig config = JsoupDocumentReaderConfig.builder()
                // 只提取p标签段落
                .selector("ne-p")
                // 文件编码
                .charset("UTF-8")
                // 包含超链接
                .includeLinkUrls(true)
                // 提取meta标签的元数据
                .metadataTags(List.of("author", "date"))
                // 添加自定义元数据
                .additionalMetadata("filename", file.getName())
                .build();

        return new JsoupDocumentReader(resource, config).get();
    }
}
