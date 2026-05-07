package cn.hollis.llm.mentor.rag.reader;

import org.springframework.ai.document.Document;
import org.springframework.ai.reader.ExtractedTextFormatter;
import org.springframework.ai.reader.JsonReader;
import org.springframework.ai.reader.pdf.config.PdfDocumentReaderConfig;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.util.List;

@Service
public class JsonReaderStrategy implements DocumentReaderStrategy {
    @Override
    public boolean supports(File file) {

        String name = file.getName().toLowerCase();
        return name.endsWith(".json");
    }

    @Override
    public List<Document> read(File file) throws IOException {
        Resource resource = new FileSystemResource(file);
        JsonReader jsonReader = new JsonReader(resource);
        return jsonReader.get();
    }
}
