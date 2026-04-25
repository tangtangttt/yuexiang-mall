package com.yuex.data.redis.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuex.data.redis.manager.StringRedisCache;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Redis 数据迁移工具类
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MigrationUtil {

    private final StringRedisCache stringRedisCache;
    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;

    /**
     * 迁移指定模式的所有数据，重新序列化为新格式
     * @param pattern Redis key 模式
     */
    public void migrateData(String pattern) {
        try {
            Set<String> keys = redisTemplate.keys(pattern);
            if (keys == null || keys.isEmpty()) {
                log.info("没有找到需要迁移的数据，模式：{}", pattern);
                return;
            }

            log.info("开始迁移数据，共 {} 个键", keys.size());

            for (String key : keys) {
                try {
                    // 获取旧数据
                    Object value = redisTemplate.opsForValue().get(key);
                    if (value != null) {
                        // 重新设置，会使用新的序列化格式
                        redisTemplate.opsForValue().set(key, value);
                        log.debug("迁移成功：{}", key);
                    }
                } catch (Exception e) {
                    log.error("迁移失败，key: {}, 错误: {}", key, e.getMessage(), e);
                }
            }

            log.info("数据迁移完成，模式：{}", pattern);
        } catch (Exception e) {
            log.error("迁移过程中发生错误：{}", e.getMessage(), e);
        }
    }

    /**
     * 检查数据是否为新格式（包含 @type 字段）
     * @param jsonData JSON 数据字符串
     * @return 是否为新格式
     */
    public boolean isNewFormat(String jsonData) {
        if (jsonData == null || jsonData.trim().isEmpty()) {
            return false;
        }
        return jsonData.contains("@type");
    }
}