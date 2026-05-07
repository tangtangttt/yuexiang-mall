package cn.hollis.llm.mentor.mcp.service;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;

import javax.net.ssl.*;
import java.io.FileInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Duration;

public class HttpsMcpService {
    public static void createInsecureHttpsClient(String baseUrl, String endpoint) {
        try {
            // 1. 创建一个信任所有证书的 TrustManager
            TrustManager[] trustAllCerts = new TrustManager[]{
                    new X509TrustManager() {
                        public X509Certificate[] getAcceptedIssuers() {
                            return new X509Certificate[0];
                        }

                        public void checkClientTrusted(X509Certificate[] certs, String authType) {
                        }

                        public void checkServerTrusted(X509Certificate[] certs, String authType) {
                        }
                    }
            };

            // 2. 初始化 SSL 上下文，绕过校验
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, trustAllCerts, new SecureRandom());

            // 3. 创建并配置 SSLParameters 以禁用主机名验证
            SSLParameters sslParameters = new SSLParameters();
            sslParameters.setEndpointIdentificationAlgorithm(null);

            HttpClient.Builder httpClient = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(30))
                    .sslContext(sslContext)
                    .sslParameters(sslParameters);

            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl))
                    .header("Authorization", "Bearer abc123456789");

            HttpClientSseClientTransport transport = HttpClientSseClientTransport.builder(baseUrl).sseEndpoint(endpoint)
                    .clientBuilder(httpClient)
                    .requestBuilder(requestBuilder)
                    .build();
            McpSyncClient mcp = McpClient.sync(transport).build();
            mcp.initialize();
        } catch (Exception e) {
            throw new RuntimeException("创建 Insecure MCP Client 失败", e);
        }
    }


    public static void createSecureHttpsClientPoc(String baseUrl, String endpoint, String caCertPath) {
        try {
            // 1. 加载 CA 证书
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            FileInputStream fis = new FileInputStream(caCertPath);
            Certificate caCert = cf.generateCertificate(fis);
            fis.close();

            // 2. 创建 KeyStore 并导入 CA
            KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());
            ks.load(null, null);
            ks.setCertificateEntry("caCert", caCert);

            // 3. 构建 TrustManagerFactory
            TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(ks);

            // 4. 创建 SSLContext
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, tmf.getTrustManagers(), new SecureRandom());

            // 5. 使用默认 Hostname 验证
            HttpClient.Builder httpClient = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(30))
                    .sslContext(sslContext);



            // 6. 构建 SSE Transport
            HttpClientSseClientTransport transport = HttpClientSseClientTransport.builder(baseUrl)
                    .sseEndpoint(endpoint)
                    .clientBuilder(httpClient)
                    .requestBuilder(HttpRequest.newBuilder().header("Authorization", "Bearer abc123456789"))
                    .build();

            // 7. 初始化 MCP Client
            McpSyncClient mcp = McpClient.sync(transport).build();
            mcp.initialize();
            System.out.println("MCP Client 初始化成功");

        } catch (Exception e) {
            throw new RuntimeException("创建 Secure MCP Client 失败", e);
        }
    }

    public static void main(String[] args) {

//        createInsecureHttpsClient("https://127.0.0.1:8443", "/sse");
        createSecureHttpsClientPoc("https://127.0.0.1:8443",
                "/sse",
                "D:\\LLMentor\\LLMentor\\mcp\\mcp-server-sse-https\\src\\main\\resources\\mcp-server.crt");
    }
}
