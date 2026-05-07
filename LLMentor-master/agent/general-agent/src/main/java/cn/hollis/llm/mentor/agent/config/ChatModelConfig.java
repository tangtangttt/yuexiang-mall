package cn.hollis.llm.mentor.agent.config;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.model.SimpleApiKey;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.Map;

public class ChatModelConfig {

    private static final Integer DEFAULT_MAX_TOKENS = 3000;
    private static final Double DEFAULT_TEMPERATURE = 0.7;

    public static ChatModel getChatModel(Integer maxToken, Double temperature) {
        Map<String, Object> config = loadConfig();

        String baseUrl = getConfigValue(config, "spring.ai.openai.base-url");
        String apiKey = getConfigValue(config, "spring.ai.openai.api-key");
        String modelName = getConfigValue(config, "spring.ai.openai.chat.options.model");

        OpenAiChatOptions opts = new OpenAiChatOptions();
        opts.setModel(modelName);
        opts.setMaxTokens(maxToken);
        opts.setTemperature(temperature);

        ChatModel chatModel = OpenAiChatModel.builder()
                .openAiApi(OpenAiApi.builder()
                        .baseUrl(baseUrl)
                        .apiKey(new SimpleApiKey(apiKey))
                        .build())
                .defaultOptions(opts)
                .build();

        return chatModel;
    }

    public static ChatModel getChatModel() {
        return getChatModel(DEFAULT_MAX_TOKENS, DEFAULT_TEMPERATURE);
    }

    /**
     * 从 application.yml 加载配置
     */
    private static Map<String, Object> loadConfig() {
        try (InputStream input = ChatModelConfig.class.getClassLoader()
                .getResourceAsStream("application.yml")) {
            if (input == null) {
                throw new RuntimeException("无法找到 application.yml 配置文件");
            }
            Yaml yaml = new Yaml();
            return yaml.load(input);
        } catch (Exception e) {
            throw new RuntimeException("读取配置文件失败", e);
        }
    }

    /**
     * 从嵌套的配置Map中获取值，支持点号分隔的路径
     */
    @SuppressWarnings("unchecked")
    private static String getConfigValue(Map<String, Object> config, String path) {
        String[] keys = path.split("\\.");
        Object current = config;

        for (String key : keys) {
            if (current instanceof Map) {
                current = ((Map<String, Object>) current).get(key);
            } else {
                return null;
            }
        }

        return current != null ? current.toString() : null;
    }
}
