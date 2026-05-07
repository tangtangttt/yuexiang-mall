package cn.hollis.llm.mentor.langchain4j;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.math.BigDecimal;

/**
 * @param title       书名
 * @param author      作者
 * @param description 简介
 * @param price       价格
 * @author Hollis
 */
public record Book(@JsonPropertyDescription("书籍名称") String title,
                   @JsonPropertyDescription("作者") String author,
                   @JsonPropertyDescription("书籍介绍") String description,
                   @JsonPropertyDescription("价格") BigDecimal price) {

}
