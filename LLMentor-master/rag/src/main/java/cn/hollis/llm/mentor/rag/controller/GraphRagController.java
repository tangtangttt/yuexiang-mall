package cn.hollis.llm.mentor.rag.controller;

import cn.hollis.llm.mentor.rag.model.Director;
import cn.hollis.llm.mentor.rag.model.Movie;
import cn.hollis.llm.mentor.rag.service.GraphService;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.data.neo4j.core.Neo4jTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequestMapping("/rag/graph")
@RestController
public class GraphRagController {

    @Autowired
    private Neo4jTemplate neo4jTemplate;

    @Autowired
    private Neo4jClient neo4jClient;

    @Autowired
    private GraphService graphService;

    @Autowired
    private ChatModel chatModel;

    @GetMapping("/ask")
    public String ask(String movieName) {

        String context = graphService.retrieveContext(movieName);

        String prompt = """
                你是一个电影知识助手，请根据以下上下文回答问题。
                如果上下文没有足够信息，请回答“我不知道”。
                
                上下文：
                %s
                
                问题：%s
                回答：
                """.formatted(context, movieName + "的导演还执导过哪些电影？");

        return chatModel.call(prompt);
    }

    @GetMapping("/init")
    public String initData() {
        // 保存节点
        neo4jTemplate.save(new Director("张艺谋"));
        neo4jTemplate.save(new Director("陈思诚"));
        neo4jTemplate.save(new Movie("十面埋伏", 2004));
        neo4jTemplate.save(new Movie("影", 2016));
        neo4jTemplate.save(new Movie("英雄", 2002));
        neo4jTemplate.save(new Movie("误杀", 2019));

        neo4jClient.query("""
                        MATCH (p:Director {name: $name}), (m:Movie {title: $title})
                        MERGE (p)-[:DIRECTED]->(m)
                        """)
                .bind("张艺谋").to("name")
                .bind("十面埋伏").to("title")
                .run();
        neo4jClient.query("""
                        MATCH (p:Director {name: $name}), (m:Movie {title: $title})
                        MERGE (p)-[:DIRECTED]->(m)
                        """)
                .bind("张艺谋").to("name")
                .bind("影").to("title")
                .run();

        neo4jClient.query("""
                        MATCH (p:Director {name: $name}), (m:Movie {title: $title})
                        MERGE (p)-[:DIRECTED]->(m)
                        """)
                .bind("张艺谋").to("name")
                .bind("英雄").to("title")
                .run();
        neo4jClient.query("""
                        MATCH (p:Director {name: $name}), (m:Movie {title: $title})
                        MERGE (p)-[:DIRECTED]->(m)
                        """)
                .bind("陈思诚").to("name")
                .bind("误杀").to("title")
                .run();

        return "Data initialized successfully";
    }
}