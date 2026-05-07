package cn.hollis.llm.mentor.mcp.controller;

import cn.hollis.llm.mentor.mcp.service.ManualMcpClientService;
import cn.hollis.llm.mentor.mcp.service.McpClientService;
import cn.hollis.llm.mentor.mcp.service.RetrySSEMcpService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/mcp")
@Slf4j
@RequiredArgsConstructor
public class McpClientController {

    @Autowired
    private McpClientService mcpClientService;

    @Autowired
    private ManualMcpClientService manualMcpClientService;

    @Autowired
    private RetrySSEMcpService retrySSEMcpService;

    @GetMapping("/callTool")
    public Object callTool(@RequestParam("type") String type) {
        return mcpClientService.callTool(type);
    }


    @GetMapping("/chat")
    public String chat(@RequestParam("query") String query) {
        log.info("chat request => {}", query);

        return mcpClientService.chat(query);
    }

    @GetMapping("/manualChat")
    public String manualChat(@RequestParam("query") String query) {
        log.info("manualChat request => {}", query);

        return manualMcpClientService.chat(query);
    }

    @GetMapping("/retryChat")
    public String retry(@RequestParam("query") String query) {
        log.info("retry chat request => {}", query);

        return retrySSEMcpService.chat(query);
    }
}
