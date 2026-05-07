package cn.hollis.llm.mentor.rag.controller;

import cn.hollis.llm.mentor.rag.router.QueryRouteService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/rag/router")
public class RagRouterController {

    @Autowired
    private QueryRouteService queryRouteService;

    @GetMapping("/route")
    public String route(String query) {
        return queryRouteService.route(query);
    }
}
