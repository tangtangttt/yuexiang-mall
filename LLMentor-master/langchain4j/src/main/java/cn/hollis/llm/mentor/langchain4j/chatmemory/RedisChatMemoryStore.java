package cn.hollis.llm.mentor.langchain4j.chatmemory;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageDeserializer;
import dev.langchain4j.data.message.ChatMessageSerializer;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

@Component
public class RedisChatMemoryStore implements ChatMemoryStore {

    private final RedisTemplate<String, String> redisTemplate;

    public RedisChatMemoryStore(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public List<ChatMessage> getMessages(Object memoryId) {
        String key = buildKey(memoryId);
        String json = redisTemplate.opsForValue().get(key);

        if (json == null || json.isEmpty()) {
            return Collections.emptyList();
        }

        return ChatMessageDeserializer.messagesFromJson(json);
    }

    @Override
    public void updateMessages(Object memoryId, List<ChatMessage> messages) {
        String key = buildKey(memoryId);
        String json = ChatMessageSerializer.messagesToJson(messages);
        redisTemplate.opsForValue().set(key, json);
    }

    @Override
    public void deleteMessages(Object memoryId) {
        redisTemplate.delete(buildKey(memoryId));
    }

    private String buildKey(Object memoryId) {
        return "langchain4j:chat-memory:" + memoryId;
    }
}
