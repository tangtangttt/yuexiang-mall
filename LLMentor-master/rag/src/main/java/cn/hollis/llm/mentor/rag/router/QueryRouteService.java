package cn.hollis.llm.mentor.rag.router;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class QueryRouteService {

    private static final String DATASOURCE_ROUTE_PROMPT =
            """
                你需要判断用户的查询问题适合使用哪种数据库进行检索。
                如果是语义相似性搜索、文档检索、内容推荐类问题，回答'VECTOR'
                如果是关系查询、知识图谱、实体关联类问题，回答'GRAPH'
                如果是结构化数据查询、统计分析、精确匹配类问题，回答'RELATIONAL'
                如果无法确定，请回答'VECTOR'
                只回答VECTOR、GRAPH或RELATIONAL，不要其他内容。
                
                用户问题：
                {QUESTION}
                """;


    @Autowired
    private ChatModel chatModel;

    public String route(String query) {
        PromptTemplate promptTemplate = new PromptTemplate(DATASOURCE_ROUTE_PROMPT);
        promptTemplate.add("QUESTION", query);

        return chatModel.call(promptTemplate.create()).getResult().getOutput().getText();
    }

}
