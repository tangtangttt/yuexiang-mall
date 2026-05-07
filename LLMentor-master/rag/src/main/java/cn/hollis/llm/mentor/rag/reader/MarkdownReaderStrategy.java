package cn.hollis.llm.mentor.rag.reader;

import org.springframework.ai.document.Document;
import org.springframework.ai.reader.markdown.MarkdownDocumentReader;
import org.springframework.ai.reader.markdown.config.MarkdownDocumentReaderConfig;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.util.List;

@Service
public class MarkdownReaderStrategy implements DocumentReaderStrategy {
    @Override
    public boolean supports(File file) {

        String name = file.getName().toLowerCase();
        return name.endsWith(".md");
    }

    @Override
    public List<Document> read(File file) throws IOException {
        // 读取配置
        MarkdownDocumentReaderConfig config = MarkdownDocumentReaderConfig.builder()
                // 水平线分割生成新文档
                .withHorizontalRuleCreateDocument(false)
                // 不包含代码块
                .withIncludeCodeBlock(false)
                // 不包含引用
                .withIncludeBlockquote(false)
                // 添加文件名元数据
                .withAdditionalMetadata("filename", file.getName())
                .build();
        Resource resource = new FileSystemResource(file);
        return new MarkdownDocumentReader(resource,config).get();
    }
}
