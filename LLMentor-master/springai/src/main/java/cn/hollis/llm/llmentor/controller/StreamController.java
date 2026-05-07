package cn.hollis.llm.llmentor.controller;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.Executors;

@RestController
@RequestMapping("/stream")
public class StreamController {

    private static final String API_KEY = "sk-8ef405c4686e456e91f6698272253126";// 记得改成你自己的
    private static final String API_URL = "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions";

    @RequestMapping("/fakeStream")
    public String fakeStream() {

        String requestBody = """
                {
                    "model": "qwen-plus",
                    "messages": [
                        {
                            "role": "system",
                            "content": "You are a helpful assistant."
                        },
                        {
                            "role": "user",
                            "content": "你好，介绍下JAVA？"
                        }
                    ],
                    "stream": true
                }
                """;
        HttpClient client = HttpClient.newHttpClient();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_URL))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + API_KEY)
                .header("X-DashScope-SSE", "enable")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        HttpResponse<String> response = null;
        try {
            response = client.send(
                    request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }


        return response.body();
    }


    @GetMapping("/sse")
    public SseEmitter sse() {
        SseEmitter emitter = new SseEmitter(60_000L);
        Executors.newVirtualThreadPerTaskExecutor().submit(() -> {
            try {
                for (int i = 0; i < 1000; i++) {
                    emitter.send("Message " + i);
                    Thread.sleep(500);
                }
            } catch (IOException | InterruptedException e) {
                emitter.completeWithError(e);
            } finally {
                emitter.complete();
            }
        });
        return emitter;
    }

    @GetMapping("/entity")
    public ResponseEntity<StreamingResponseBody> chat() {

        StreamingResponseBody body = outputStream -> {
            for (int i = 0; i < 1000; i++) {
                String data = "Message " + i + "\n";
                outputStream.write(data.getBytes(StandardCharsets.UTF_8));
                outputStream.flush();
                try {
                    Thread.sleep(500); // 模拟延迟
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        };

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_EVENT_STREAM_VALUE)
                .body(body);
    }


    @GetMapping(value = "/flux")
    public Flux<String> fluxStream() {
        return Flux.interval(Duration.ofSeconds(1))
                .map(seq -> "Message " + seq);
    }

}