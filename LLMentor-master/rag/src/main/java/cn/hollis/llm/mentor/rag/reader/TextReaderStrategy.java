package cn.hollis.llm.mentor.rag.reader;

import org.springframework.ai.document.Document;
import org.springframework.ai.reader.TextReader;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.util.List;
@Service
public class TextReaderStrategy implements DocumentReaderStrategy {
    @Override
    public boolean supports(File file) {
        String name = file.getName().toLowerCase();
        return name.endsWith(".txt") || name.endsWith(".tex") || name.endsWith(".text");
    }

    @Override
    public List<Document> read(File file) throws IOException {
        Resource resource = new FileSystemResource(file);
        TextReader textReader = new TextReader(resource);
        return textReader.get();
    }
}
