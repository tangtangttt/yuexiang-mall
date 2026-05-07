package cn.hollis.llm.mentor.rag.model;

import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;

@Node("Movie")
public class Movie {
    @Id
    private String title;

    private int year;

    public Movie() {
    }

    public Movie(String title, int year) {
        this.title = title;
        this.year = year;
    }

    // Getters
    public String getTitle() {
        return title;
    }

    public int getYear() {
        return year;
    }
}