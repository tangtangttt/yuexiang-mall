package cn.hollis.llm.mentor.rag.es;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.Refresh;
import co.elastic.clients.elasticsearch.core.*;
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest;
import co.elastic.clients.elasticsearch.indices.ExistsRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
public class ElasticSearchService {

    @Autowired
    private ElasticsearchClient client;

    private final ObjectMapper mapper = new ObjectMapper();

    private static final String INDEX_NAME = "rag_docs";

    private static final String FIELD_CONTENT = "content";

    @PostConstruct
    public void init() {
        try {
            if (!indexExists(INDEX_NAME)) {
                createIndex();
                log.info("ES index [{}] created with IK analyzer!", INDEX_NAME);
            } else {
                log.info("ES index [{}] already exists, skip creation.", INDEX_NAME);
            }
        } catch (Exception e) {
            log.error("Failed to create ES index: {}", e.getMessage(), e);
        }
    }

    /**
     * 创建索引（IK 分词 + 停用词 + lowercase）
     */
    public void createIndex() throws Exception {
        // 1. 设置索引配置（settings）和 mapping
        String settingsAndMappingJson = """
                {
                  "settings": {
                    "number_of_shards": 1,
                    "number_of_replicas": 0,
                    "analysis": {
                      "filter": {
                        "my_stop_filter": {
                          "type": "stop",
                          "stopwords": "_chinese_"
                        }
                      },
                      "analyzer": {
                        "ik_max": {
                          "type": "custom",
                          "tokenizer": "ik_max_word",
                          "filter": ["lowercase", "my_stop_filter"]
                        },
                        "ik_smart": {
                          "type": "custom",
                          "tokenizer": "ik_smart",
                          "filter": ["lowercase", "my_stop_filter"]
                        }
                      }
                    }
                  },
                  "mappings": {
                    "properties": {
                      "id": { "type": "keyword" },
                      "content": {
                        "type": "text",
                        "analyzer": "ik_max",
                        "search_analyzer": "ik_smart",
                        "fields": {
                          "smart": {
                            "type": "text",
                            "analyzer": "ik_smart",
                            "search_analyzer": "ik_smart"
                          }
                        }
                      },
                      "metadata": {
                        "type": "object",
                        "properties": {
                          "source": { "type": "keyword" },
                          "category": { "type": "keyword" },
                          "orderId": { "type": "keyword" }
                        }
                      }
                    }
                  }
                }
                """;

        CreateIndexRequest request = CreateIndexRequest.of(b -> b
                .index(INDEX_NAME)
                .withJson(new StringReader(settingsAndMappingJson))
        );

        // 3. 创建索引
        client.indices().create(request);
    }

    /**
     * 单条存储
     */
    public void indexSingle(EsDocumentChunk doc) throws Exception {
        if (doc == null || doc.getId() == null) {
            throw new IllegalArgumentException("Document or ID cannot be null");
        }

        String docJson = mapper.writeValueAsString(doc);

        IndexRequest<EsDocumentChunk> request = IndexRequest.of(b -> b
                .index(INDEX_NAME)
                .id(doc.getId())
                .withJson(new StringReader(docJson))
                .refresh(Refresh.True)
        );

        client.index(request);
        log.debug("Indexed doc id={}", doc.getId());
    }

    /**
     * 批量存储
     */
    public void bulkIndex(List<EsDocumentChunk> docs) throws Exception {
        if (docs == null || docs.isEmpty()) return;

        BulkRequest.Builder bulkBuilder = new BulkRequest.Builder();

        for (EsDocumentChunk doc : docs) {
            bulkBuilder.operations(op -> op
                    .index(idx -> idx
                            .index(INDEX_NAME)
                            .id(doc.getId())
                            .document(doc)
                    )
            );
        }

        bulkBuilder.refresh(Refresh.True);

        BulkResponse response = client.bulk(bulkBuilder.build());
        if (response.errors()) {
            log.error("Bulk indexing completed with failures");
            response.items().forEach(item -> {
                if (item.error() != null) {
                    log.error("Failed to index doc {}: {}", item.id(), item.error().reason());
                }
            });
        } else {
            log.info("Successfully indexed {} documents", docs.size());
        }
    }

    public boolean indexExists(String indexName) throws IOException {
        ExistsRequest request = ExistsRequest.of(b -> b.index(indexName));
        return client.indices().exists(request).value();
    }

    /**
     * 中文检索 - ik_max_word 建库 + ik_smart 检索
     */
    public List<EsDocumentChunk> searchByKeyword(String keyword) throws Exception {
        return searchByKeyword(keyword, 5, false);
    }

    /**
     * 中文检索：ik_max_word / ik_smart 切换
     */
    public List<EsDocumentChunk> searchByKeyword(String keyword, int size, boolean useSmartAnalyzer) throws Exception {
        String field = useSmartAnalyzer ? FIELD_CONTENT + ".smart" : FIELD_CONTENT;

        SearchRequest request = SearchRequest.of(b -> b
                .index(INDEX_NAME)
                .query(q -> q
                        .match(m -> m
                                .field(field)
                                .query(keyword)
                        )
                )
                .size(size)
        );

        SearchResponse<EsDocumentChunk> response = client.search(request, EsDocumentChunk.class);

        List<EsDocumentChunk> result = new ArrayList<>();
        response.hits().hits().forEach(hit -> {
            if (hit.source() != null) {
                result.add(hit.source());
            }
        });

        return result;
    }
}
