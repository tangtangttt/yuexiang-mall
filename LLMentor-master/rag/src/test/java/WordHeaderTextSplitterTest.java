import cn.hollis.llm.mentor.rag.splitter.WordHeaderTextSplitter;
import org.springframework.ai.document.Document;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;

public class WordHeaderTextSplitterTest {


    public static void main(String[] args) {

        System.out.println("========== 示例: 启用父子关系模式 ==========\n");

        // 创建分割器，启用父子关系
        WordHeaderTextSplitter splitter = new WordHeaderTextSplitter(
                Arrays.asList(1, 2, 3),
                false,
                false,
                false,  // 启用父子关系
                1000, 100
        );

        String filePath = "/Users/hollis/Downloads/OA系统使用操作手册.docx";
        try (InputStream is = new FileInputStream(filePath)) {
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("wordInputStream", is);

            Document doc = new Document("", metadata);
            List<Document> segments = splitter.apply(Collections.singletonList(doc));

            // 打印父子关系
            System.out.println("文档结构树：\n");
            for (Document segment : segments) {
                Integer level = (Integer) segment.getMetadata().get("headingLevel");
                String chunkId = (String) segment.getMetadata().get("chunkId");
                String parentChunkId = (String) segment.getMetadata().get("parentChunkId");
                Integer segmentIndex = (Integer) segment.getMetadata().get("segmentIndex");

                if (level != null) {
                    String indent = "  ".repeat(level - 1);
                    System.out.println(indent + "- [级别" + level + "] " +
                            segment.getText());
                    System.out.println(indent + "  ChunkId: " + chunkId);
                    if (parentChunkId != null) {
                        System.out.println(indent + "  ParentId: " + parentChunkId);
                    }

                    if (segmentIndex != null) {
                        System.out.println(indent + "  segmentIndex: " + segmentIndex);
                    }
                }

                System.out.println("==============");
                System.out.println("==============");
                System.out.println("==============");
                System.out.println("==============");
            }
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
