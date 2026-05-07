package cn.hollis.llm.mentor.rag.es;

import lombok.Data;

import java.util.Map;

@Data
public class EsDocumentChunk {

    private String id;
    private String content;
    private Map<String, Object> metadata;
}