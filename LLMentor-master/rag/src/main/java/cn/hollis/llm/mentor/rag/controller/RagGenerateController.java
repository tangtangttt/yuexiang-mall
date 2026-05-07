package cn.hollis.llm.mentor.rag.controller;

import cn.hollis.llm.mentor.rag.generate.SqlQueryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/rag/generate")
public class RagGenerateController {

    @Autowired
    private SqlQueryService sqlQueryService;

    @RequestMapping("/sql")
    public String sql(String query) {
        return sqlQueryService.text2sql(query);
    }
}
