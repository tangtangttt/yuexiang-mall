package cn.hollis.llm.mentor.rag.model;

import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;

@Node("Director")
public class Director {
    @Id
    private String name;

    public Director() {
    }

    public Director(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }
}