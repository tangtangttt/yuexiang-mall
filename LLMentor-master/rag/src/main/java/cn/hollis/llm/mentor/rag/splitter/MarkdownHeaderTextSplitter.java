package cn.hollis.llm.mentor.rag.splitter;

import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TextSplitter;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Markdown文档分割器，基于标题层级进行文档分段
 * 支持保留元数据、父子分段关系等高级特性
 *
 * @author andyflury （https://github.com/langchain4j/langchain4j/issues/574 ）
 * @author Hollis, 增加对父子分段的支持
 */
public class MarkdownHeaderTextSplitter extends TextSplitter {

    /** 需要分割的标题列表，按标题标记长度倒序排列 */
    private List<Map.Entry<String, String>> headersToSplitOn;

    /** 是否按行返回结果 */
    private boolean returnEachLine;

    /** 是否剥离标题行本身 */
    private boolean stripHeaders;

    /** 是否启用父子分段模式 */
    private boolean parentChildModel;

    /**
     * 构造函数
     *
     * @param headersToSplitOn 标题分割映射表，key为标题标记（如"#"、"##"），value为元数据中的键名
     * @param returnEachLine 是否按行返回结果，false时会聚合相同元数据的行
     * @param stripHeaders 是否在结果中移除标题行
     * @param parentChildModel 是否启用父子分段模式，启用后会在元数据中添加parentChunkId
     */
    public MarkdownHeaderTextSplitter(Map<String, String> headersToSplitOn, boolean returnEachLine, boolean stripHeaders, boolean parentChildModel) {
        // 按标题标记长度倒序排列，确保优先匹配更长的标记（如"###"优先于"##"）
        this.headersToSplitOn = headersToSplitOn.entrySet().stream()
                .sorted(Comparator.comparingInt(e -> -e.getKey().length()))
                .collect(Collectors.toList());
        this.returnEachLine = returnEachLine;
        this.stripHeaders = stripHeaders;
        this.parentChildModel = parentChildModel;
    }

    /**
     * 重写apply方法以支持元数据的传递
     */
    @Override
    public List<Document> apply(List<Document> documents) {
        if (documents == null || documents.isEmpty()) {
            return Collections.emptyList();
        }

        List<Document> result = new ArrayList<>();
        for (Document doc : documents) {
            List<DocumentWithMetadata> segments = splitWithMetadata(doc.getText(), doc.getMetadata());
            for (DocumentWithMetadata segment : segments) {
                result.add(new Document(segment.getContent(), segment.getMetadata()));
            }
        }
        return result;
    }

    /**
     * 简化版分割方法，不保留元数据
     *
     * @param text 待分割的文本
     * @return 分割后的文本片段列表
     */
    @Override
    protected List<String> splitText(String text) {
        // 简化版本，仅返回文本内容
        return splitWithMetadata(text, new HashMap<>()).stream()
                .map(DocumentWithMetadata::getContent)
                .collect(Collectors.toList());
    }

    /**
     * 核心分割逻辑，保留元数据
     *
     * @param text 待分割的文本
     * @param baseMetadata 基础元数据，会被传递到每个分段中
     * @return 带有元数据的文档片段列表
     */
    private List<DocumentWithMetadata> splitWithMetadata(String text, Map<String, Object> baseMetadata) {
        List<String> lines = Arrays.asList(text.split("\n"));
        List<Line> linesWithMetadata = new ArrayList<>();
        List<String> currentContent = new ArrayList<>();
        Map<String, Object> currentMetadata = new HashMap<>(baseMetadata);
        List<Header> headerStack = new ArrayList<>();  // 标题栈，用于追踪当前的标题层级结构
        Map<String, Object> initialMetadata = new HashMap<>(baseMetadata);

        boolean inCodeBlock = false;  // 是否在代码块中
        String openingFence = "";     // 代码块的开始标记

        for (String line : lines) {
            String strippedLine = line.trim();

            // 处理代码块标记，代码块内的内容不作为标题处理
            if (!inCodeBlock) {
                if (strippedLine.startsWith("```")) {
                    inCodeBlock = !inCodeBlock;
                    openingFence = "```";
                } else if (strippedLine.startsWith("~~~")) {
                    inCodeBlock = !inCodeBlock;
                    openingFence = "~~~";
                }
            } else {
                if (strippedLine.startsWith(openingFence)) {
                    inCodeBlock = false;
                    openingFence = "";
                }
            }

            // 代码块内的内容直接添加，不做标题检测
            if (inCodeBlock) {
                currentContent.add(strippedLine);
                continue;
            }

            // 检测并处理标题行
            interrupted:
            {
                for (Map.Entry<String, String> header : headersToSplitOn) {
                    String sep = header.getKey();    // 标题标记，如"#"、"##"
                    String name = header.getValue(); // 元数据中的键名

                    // 判断是否为有效的标题行
                    if (strippedLine.startsWith(sep) && (strippedLine.length() == sep.length() || strippedLine.charAt(sep.length()) == ' ')) {
                        if (name != null) {
                            // 计算当前标题级别（统计#的个数）
                            int currentHeaderLevel = (int) sep.chars().filter(ch -> ch == '#').count();

                            // 维护标题栈：移除所有级别大于等于当前级别的标题
                            // 这样可以正确处理标题层级关系，如从### 回退到 ##
                            while (!headerStack.isEmpty() && headerStack.get(headerStack.size() - 1).getLevel() >= currentHeaderLevel) {
                                Header poppedHeader = headerStack.remove(headerStack.size() - 1);
                                initialMetadata.remove(poppedHeader.getName());
                            }

                            // 将当前标题加入栈，并更新元数据
                            Header headerType = new Header(currentHeaderLevel, name, strippedLine.substring(sep.length()).trim());
                            headerStack.add(headerType);
                            initialMetadata.put(name, headerType.getData());
                            initialMetadata.put("headerLevel", currentHeaderLevel);
                            // 为每个分段生成唯一ID，用于后续建立父子关系
                            String currentChunkId = UUID.randomUUID().toString();
                            initialMetadata.put("chunkId", currentChunkId);
                        }

                        // 遇到新标题时，保存之前累积的内容
                        if (!currentContent.isEmpty()) {
                            linesWithMetadata.add(new Line(String.join("\n", currentContent), currentMetadata));
                            currentContent.clear();
                        }

                        // 根据stripHeaders配置决定是否保留标题行
                        if (!stripHeaders) {
                            currentContent.add(strippedLine);
                        }

                        break interrupted;
                    }
                }

                // 处理非标题行
                if (!strippedLine.isEmpty()) {
                    currentContent.add(strippedLine);
                } else if (!currentContent.isEmpty()) {
                    // 遇到空行时，保存当前累积的内容
                    linesWithMetadata.add(new Line(String.join("\n", currentContent), currentMetadata));
                    currentContent.clear();
                }
            }

            // 更新当前元数据为最新的标题信息
            currentMetadata = new HashMap<>(initialMetadata);
        }

        // 处理最后累积的内容
        if (!currentContent.isEmpty()) {
            linesWithMetadata.add(new Line(String.join("\n", currentContent), currentMetadata));
        }

        // 根据配置决定返回方式
        List<DocumentWithMetadata> segments;
        if (!returnEachLine) {
            // 聚合模式：将相同元数据的行合并
            segments = aggregateLinesToChunks(linesWithMetadata);
        } else {
            // 逐行模式：保持每行独立
            segments = linesWithMetadata.stream()
                    .map(line -> new DocumentWithMetadata(line.getContent(), line.getMetadata()))
                    .collect(Collectors.toList());
        }

        return segments;
    }

    /**
     * 聚合行为分块
     * 将具有相同元数据的行合并为一个分块，并处理父子关系
     *
     * @param lines 待聚合的行列表
     * @return 聚合后的文档片段列表
     */
    private List<DocumentWithMetadata> aggregateLinesToChunks(List<Line> lines) {
        List<Line> aggregatedChunks = new ArrayList<>();
        for (Line line : lines) {
            // 情况1：元数据相同，直接合并到上一个分块
            if (!aggregatedChunks.isEmpty() && aggregatedChunks.get(aggregatedChunks.size() - 1).getMetadata().equals(line.getMetadata())) {
                Line last = aggregatedChunks.get(aggregatedChunks.size() - 1);
                last.setContent(last.getContent() + "  \n" + line.getContent());
            }
            // 情况2：元数据不同但上一行以标题结尾且未剥离标题，则也合并
            // 这样可以将标题和其下的第一段内容合并在一起
            else if (!aggregatedChunks.isEmpty() && !aggregatedChunks.get(aggregatedChunks.size() - 1).getMetadata().equals(line.getMetadata())
                    && aggregatedChunks.get(aggregatedChunks.size() - 1).getMetadata().size() < line.getMetadata().size()
                    && aggregatedChunks.get(aggregatedChunks.size() - 1).getContent().split("\n")[aggregatedChunks.get(aggregatedChunks.size() - 1).getContent().split("\n").length - 1].startsWith("#") && !stripHeaders) {

                Line last = aggregatedChunks.get(aggregatedChunks.size() - 1);
                last.setContent(last.getContent() + "  \n" + line.getContent());
            }
            // 情况3：创建新分块
            else {
                aggregatedChunks.add(line);
            }
        }

        // 处理父子分段关系
        if (parentChildModel) {
            try {
                // 遍历所有分块，为非顶级标题建立父子关系
                for (int i = 0; i < aggregatedChunks.size(); i++) {
                    Map<String, Object> currentMetaData = aggregatedChunks.get(i).getMetadata();
                    Integer headerLevel = (Integer) currentMetaData.get("headerLevel");
                    // 顶级标题（level=1）或无标题的分块跳过
                    if (headerLevel == null || headerLevel == 1) {
                        continue;
                    }

                    // 向前查找第一个级别更低的标题作为父节点
                    if (headerLevel > 1) {
                        for (int j = i - 1; j >= 0; j--) {
                            Map<String, Object> lastMetaData = aggregatedChunks.get(j).getMetadata();
                            Integer lastHeaderLevel = (Integer) lastMetaData.get("headerLevel");
                            if (lastHeaderLevel != null && lastHeaderLevel < headerLevel) {
                                // 将父节点的chunkId设置为当前节点的parentChunkId
                                currentMetaData.put("parentChunkId", lastMetaData.get("chunkId"));
                                break;
                            }
                        }
                    }
                }
            } catch (Exception e) {
                System.out.println("父子模式转换失败，" + e.getMessage());
            }
        }

        return aggregatedChunks.stream()
                .map(chunk -> new DocumentWithMetadata(chunk.getContent(), chunk.getMetadata()))
                .collect(Collectors.toList());
    }

    /**
     * 内部类：表示带有元数据的文本行
     */
    public static class Line {
        /** 文本内容 */
        private String content;
        /** 元数据信息 */
        private Map<String, Object> metadata;

        public Line(String content, Map<String, Object> metadata) {
            this.content = content;
            this.metadata = metadata;
        }

        public String getContent() {
            return content;
        }

        public void setContent(String content) {
            this.content = content;
        }

        public Map<String, Object> getMetadata() {
            return metadata;
        }

        public void setMetadata(Map<String, Object> metadata) {
            this.metadata = metadata;
        }
    }

    /**
     * 内部类：表示Markdown标题
     */
    public static class Header {
        /** 标题级别（1-6） */
        private int level;
        /** 元数据中的键名 */
        private String name;
        /** 标题文本内容（不含#标记） */
        private String data;

        public Header(int level, String name, String data) {
            this.level = level;
            this.name = name;
            this.data = data;
        }

        public int getLevel() {
            return level;
        }

        public void setLevel(int level) {
            this.level = level;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getData() {
            return data;
        }

        public void setData(String data) {
            this.data = data;
        }
    }

    /**
     * 内部类：携带元数据的文档片段
     */
    private static class DocumentWithMetadata {
        private final String content;
        private final Map<String, Object> metadata;

        public DocumentWithMetadata(String content, Map<String, Object> metadata) {
            this.content = content;
            this.metadata = new HashMap<>(metadata);
        }

        public String getContent() {
            return content;
        }

        public Map<String, Object> getMetadata() {
            return metadata;
        }
    }
}