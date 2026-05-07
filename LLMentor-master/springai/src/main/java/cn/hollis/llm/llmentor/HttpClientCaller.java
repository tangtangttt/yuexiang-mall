package cn.hollis.llm.llmentor;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class HttpClientCaller {

    // 记得改成你自己的
    private static final String API_KEY = "sk-8ef405c4686e456e91f6698272253126";
    private static final String API_URL = "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions";

    public static void main(String[] args) throws IOException, InterruptedException {
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

        HttpResponse<String> response = client.send(
                request, HttpResponse.BodyHandlers.ofString());


        System.out.println(response.body());
    }
}
