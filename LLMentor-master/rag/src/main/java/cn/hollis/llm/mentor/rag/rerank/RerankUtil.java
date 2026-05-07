package cn.hollis.llm.mentor.rag.rerank;

import cn.hollis.llm.mentor.rag.es.EsDocumentChunk;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
public class RerankUtil {

    /**
     * RRF 算法融合向量检索和关键词检索结果
     * 公式：RRF Score = Σ(1/(k + rank_i))，其中 k 为常数（通常取60），rank_i 为文档在第i个检索结果中的排名
     */
    public static List<String> rrfFusion(List<Document> vectorDocs, List<EsDocumentChunk> keywordDocs, int topK) {
        // 常数 k，控制低排名文档的权重
        final int K = 60;
        // 存储每个文档ID的RRF得分
        Map<String, Double> rrfScores = new HashMap<>();
        // 存储文档ID到chunkId的映射
        Map<String, String> idToChunkId = new HashMap<>();

        // 处理向量检索结果（排名从1开始）
        for (int i = 0; i < vectorDocs.size(); i++) {
            Document doc = vectorDocs.get(i);
            String docId = doc.getId();
            // 获取元数据中的chunkId
            String chunkId = doc.getMetadata().getOrDefault("chunkId", "unknown").toString();
            idToChunkId.put(docId, chunkId);
            // 排名从1开始
            int rank = i + 1;
            double score = 1.0 / (K + rank);
            rrfScores.put(docId, rrfScores.getOrDefault(docId, 0.0) + score);
        }

        // 处理关键词检索结果（排名从1开始）
        for (int i = 0; i < keywordDocs.size(); i++) {
            EsDocumentChunk doc = keywordDocs.get(i);
            String docId = doc.getId();
            // 获取元数据中的chunkId
            String chunkId = doc.getMetadata().getOrDefault("chunkId", "unknown").toString();
            idToChunkId.put(docId, chunkId);
            // 排名从1开始
            int rank = i + 1;
            double score = 1.0 / (K + rank);
            rrfScores.put(docId, rrfScores.getOrDefault(docId, 0.0) + score);
        }

        // 收集所有文档ID并按RRF得分降序排序，同时限制返回topK条
        List<String> sortedDocIds = rrfScores.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .map(Map.Entry::getKey)
                .limit(topK)
                .collect(Collectors.toList());

        // 打印每个文本块的chunkId和分数
        String scoresLog = sortedDocIds.stream()
                .map(docId -> {
                    String chunkId = idToChunkId.getOrDefault(docId, "unknown");
                    double score = rrfScores.getOrDefault(docId, 0.0);
                    return String.format("chunkId: %s, RRF Score: %.4f", chunkId, score);
                })
                .collect(Collectors.joining("; "));

        log.info("RRF融合后top{}结果：{}", topK, scoresLog);

        // 构建文档ID到内容的映射
        Map<String, String> idToContent = new HashMap<>();
        vectorDocs.forEach(doc -> idToContent.putIfAbsent(doc.getId(), doc.getText()));
        keywordDocs.forEach(doc -> idToContent.putIfAbsent(doc.getId(), doc.getContent()));

        // 按排序后的ID提取文档内容
        return sortedDocIds.stream()
                .map(idToContent::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    /**
     * 使用qwen3-rerank重排序
     */
    public static List<String> rerankFusion(List<Document> vectorDocs, List<EsDocumentChunk> keywordDocs, String query, int topK) throws Exception {
        Map<String, String> idToContent = new LinkedHashMap<>();
        Map<String, String> idToChunkId = new HashMap<>();

        vectorDocs.forEach(doc -> {
            String docId = doc.getId();
            idToContent.putIfAbsent(docId, doc.getText());
            String chunkId = doc.getMetadata().getOrDefault("chunkId", docId).toString();
            idToChunkId.putIfAbsent(docId, chunkId);
        });

        keywordDocs.forEach(doc -> {
            String docId = doc.getId();
            idToContent.putIfAbsent(docId, doc.getContent());
            String chunkId = doc.getMetadata().getOrDefault("chunkId", docId).toString();
            idToChunkId.putIfAbsent(docId, chunkId);
        });

        List<String> documents = new ArrayList<>(idToContent.values());
        if (documents.isEmpty()) {
            log.info("没有检索到任何文档，无需重排序");
            return Collections.emptyList();
        }

        String url = "https://dashscope.aliyuncs.com/api/v1/services/rerank/text-rerank/text-rerank";
        HttpHeaders headers = new HttpHeaders();
        // 补充自己的apikey
        headers.set("Authorization", "Bearer sk-8ef405c4686e456e91f6698272253126");
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", "gte-rerank-v2");

        Map<String, Object> input = new HashMap<>();
        input.put("query", query);
        input.put("documents", documents);
        requestBody.put("input", input);

        Map<String, Object> parameters = new HashMap<>();
        parameters.put("return_documents", true);
        parameters.put("top_n", topK);
        parameters.put("instruct", "Given a web search query, retrieve relevant passages that answer the query.");
        requestBody.put("parameters", parameters);

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);
        RestTemplate restTemplate = new RestTemplate();
        restTemplate.setRequestFactory(new SimpleClientHttpRequestFactory() {{
            setConnectTimeout(5000);
            setReadTimeout(10000);
        }});

        ResponseEntity<Map> response = restTemplate.postForEntity(url, request, Map.class);

        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new RuntimeException("重排序API调用失败: " + response.getStatusCode() + "，响应: " + response.getBody());
        }

        Map<String, Object> responseBody = response.getBody();
        if (responseBody == null || !responseBody.containsKey("output")) {
            throw new RuntimeException("API响应格式异常，缺少output字段: " + responseBody);
        }

        Map<String, Object> output = (Map<String, Object>) responseBody.get("output");
        List<Map<String, Object>> rerankedResults = (List<Map<String, Object>>) output.get("results");
        if (rerankedResults == null || rerankedResults.isEmpty()) {
            log.warn("重排序返回空结果: {}", output);
            return Collections.emptyList();
        }

        List<String> result = new ArrayList<>();
        List<String> rankLogs = new ArrayList<>();

        for (int i = 0; i < rerankedResults.size(); i++) {
            Map<String, Object> item = rerankedResults.get(i);
            String text = (String) ((Map<String, Object>) item.get("document")).get("text");
            Double score = null;
            if (item.containsKey("relevance_score")) {
                score = ((Number) item.get("relevance_score")).doubleValue();
            } else if (item.containsKey("score")) {
                score = ((Number) item.get("score")).doubleValue();
            }

            if (text != null) {
                result.add(text);

                String matchedChunkId = "unknown";
                for (Map.Entry<String, String> entry : idToContent.entrySet()) {
                    if (entry.getValue().equals(text)) {
                        matchedChunkId = idToChunkId.getOrDefault(entry.getKey(), "unknown");
                        break;
                    }
                }

                rankLogs.add(String.format("排名 %d: chunkId=%s, 分数=%.4f",
                        i + 1, matchedChunkId, score != null ? score : 0.0));
            }
        }

        log.info("qwen3-rerank重排序结果：{}", String.join("; ", rankLogs));
        log.info("重排序后返回{}条文档，原始合并{}条", result.size(), documents.size());

        return result;
    }

}
