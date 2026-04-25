package com.yuex.data.redis.manager;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@AllArgsConstructor
public class StringRedisCache {

    private final StringRedisTemplate stringRedisTemplate;
    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;

    /**
     * lua 原子递增脚本
     */
    public static String buildLuaIncrKeyScript() {
        return """
                local key = KEYS[1]
                local limit = ARGV[1]
                local c = redis.call('get', key)
                if c and tonumber(c) > tonumber(limit) then
                    redis.call('set', key, 0)
                    return c
                end
                return redis.call('incr', key)
                """;
    }

    /**
     * 缓存基本的对象，Integer、String、实体类等
     *
     * @param key   缓存的键值
     * @param value 缓存的值
     */
    public <T> void setCacheObject(final String key, final T value) {
        try {
            if (value == null) {
                return;
            }
            // 使用 RedisTemplate 存储对象，会自动序列化为 JSON
            redisTemplate.opsForValue().set(key, value);
        } catch (Exception e) {
            log.error("缓存对象失败：{}", e.getMessage(), e);
        }
    }

    /**
     * 缓存字符串值
     *
     * @param key   缓存的键值
     * @param value 缓存的字符串值
     */
    public void setCacheValue(final String key, final String value) {
        try {
            stringRedisTemplate.opsForValue().set(key, value);
        } catch (Exception e) {
            log.error("缓存字符串失败：{}", e.getMessage(), e);
        }
    }

    /**
     * 缓存基本的对象，带过期时间
     *
     * @param key      缓存的键值
     * @param value    缓存的值
     * @param timeout  时间
     * @param timeUnit 时间颗粒度
     */
    public <T> void setCacheObject(final String key, final T value, final Integer timeout, final TimeUnit timeUnit) {
        try {
            if (value == null) {
                return;
            }
            // 使用 RedisTemplate 存储对象，会自动序列化为 JSON
            redisTemplate.opsForValue().set(key, value, timeout, timeUnit);
        } catch (Exception e) {
            log.error("缓存对象失败：{}", e.getMessage(), e);
        }
    }

    public <T> void setCacheObject(final String key, final T value, final Integer timeout) {
        setCacheObject(key, value, timeout, TimeUnit.SECONDS);
    }

    public <T> void setCacheObject(final String key, final T value, final Long timeout, final TimeUnit timeUnit) {
        try {
            if (value == null) {
                return;
            }
            // 使用 RedisTemplate 存储对象，会自动序列化为 JSON
            redisTemplate.opsForValue().set(key, value, timeout, timeUnit);
        } catch (Exception e) {
            log.error("缓存对象失败：{}", e.getMessage(), e);
        }
    }

    public <T> void setCacheObject(final String key, final T value, final Long timeout) {
        setCacheObject(key, value, timeout, TimeUnit.SECONDS);
    }

    /**
     * 缓存字符串
     *
     * @param key   缓存的键值
     * @param value 缓存的值
     */
    public void setCacheString(final String key, final String value) {
        try {
            stringRedisTemplate.opsForValue().set(key, value);
        } catch (Exception e) {
            log.error("缓存字符串失败：{}", e.getMessage(), e);
        }
    }

    /**
     * 获取字符串值 - 直接从StringRedisTemplate获取，避免类型转换
     *
     * @param key 缓存键值
     * @return 缓存键值对应的数据
     */
    public String getCacheStringDirectly(final String key) {
        try {
            return stringRedisTemplate.opsForValue().get(key);
        } catch (Exception e) {
            log.error("获取缓存字符串失败：{}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * 缓存字符串，带过期时间
     *
     * @param key      缓存的键值
     * @param value    缓存的值
     * @param timeout  时间
     * @param timeUnit 时间颗粒度
     */
    public void setCacheString(final String key, final String value, final Long timeout, final TimeUnit timeUnit) {
        try {
            stringRedisTemplate.opsForValue().set(key, value, timeout, timeUnit);
        } catch (Exception e) {
            log.error("缓存字符串失败：{}", e.getMessage(), e);
        }
    }

    /**
     * 仅当 key 不存在时写入字符串（SET NX EX），用于幂等、防重复提交等场景。
     *
     * @return true 表示写入成功（首次）；false 表示 key 已存在或执行异常
     */
    public boolean setStringIfAbsent(final String key, final String value, final long timeout, final TimeUnit timeUnit) {
        try {
            return Boolean.TRUE.equals(stringRedisTemplate.opsForValue().setIfAbsent(key, value, timeout, timeUnit));
        } catch (Exception e) {
            log.error("setStringIfAbsent 失败：{}", e.getMessage(), e);
            return false;
        }
    }

    public void setCacheString(final String key, final String value, final Long timeout) {
        setCacheString(key, value, timeout, TimeUnit.SECONDS);
    }

    /**
     * 设置有效时间
     *
     * @param key     Redis 键
     * @param timeout 超时时间
     * @return true=设置成功；false=设置失败
     */
    public boolean expire(final String key, final long timeout) {
        return expire(key, timeout, TimeUnit.SECONDS);
    }

    /**
     * 判断缓存是否存在
     *
     * @param key 缓存键值
     * @return true=存在；false=不存在
     */
    public boolean existsKey(String key) {
        return Boolean.TRUE.equals(stringRedisTemplate.hasKey(key));
    }

    /**
     * 设置有效时间
     *
     * @param key     Redis 键
     * @param timeout 超时时间
     * @param unit    时间单位
     * @return true=设置成功；false=设置失败
     */
    public boolean expire(final String key, final long timeout, final TimeUnit unit) {
        return Boolean.TRUE.equals(stringRedisTemplate.expire(key, timeout, unit));
    }

    /**
     * 获得缓存的基本对象
     *
     * @param key 缓存键值
     * @return 缓存键值对应的数据
     */
    public <T> T getCacheObject(final String key) {
        try {
            Object value = redisTemplate.opsForValue().get(key);
            if (value == null) {
                return null;
            }
            if (value instanceof String) {
                // 如果是字符串，使用 ObjectMapper 反序列化
                return objectMapper.readValue((String) value, (Class<T>) Object.class);
            } else if (value instanceof Map || value instanceof List) {
                // 如果是 Map 或 List，转换为 JSON 字符串再反序列化
                String json = objectMapper.writeValueAsString(value);
                return objectMapper.readValue(json, (Class<T>) Object.class);
            }
            // 直接返回，期望已经是正确的类型
            return (T) value;
        } catch (Exception e) {
            log.error("获取缓存对象失败：{}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * 获取 LoginUserDetail 缓存对象
     *
     * @param key 缓存键值
     * @return LoginUserDetail 对象
     */
    public Object getLoginUserDetail(final String key) {
        try {
            Object value = redisTemplate.opsForValue().get(key);
            if (value == null) {
                return null;
            }

            if (value instanceof String) {
                // 如果是字符串，使用 ObjectMapper 反序列化
                String json = (String) value;
                return objectMapper.readValue(json, Object.class);
            } else if (value instanceof Map) {
                // 如果是 Map，转换成 JSON 字符串再反序列化
                String json = objectMapper.writeValueAsString(value);
                return objectMapper.readValue(json, Object.class);
            } else {
                // 其他情况直接返回
                return value;
            }
        } catch (Exception e) {
            log.error("获取 LoginUserDetail 缓存失败 - key: {}, error: {}", key, e.getMessage(), e);
            return null;
        }
    }

    /**
     * 获取指定类型的缓存对象
     *
     * @param key   缓存键值
     * @param clazz 返回类型
     * @return 缓存键值对应的数据
     */
    public <T> T getCacheObject(final String key, Class<T> clazz) {
        try {
            Object value = redisTemplate.opsForValue().get(key);
            if (value == null) {
                return null;
            }

            log.debug("获取缓存 - key: {}, value type: {}", key, value.getClass().getName());

            if (value instanceof String) {
                return objectMapper.readValue((String) value, clazz);
            }
            // GenericJackson2JsonRedisSerializer 可能返回 LinkedHashMap 等，cast 会 ClassCastException；统一走 convertValue
            return objectMapper.convertValue(value, clazz);
        } catch (Exception e) {
            log.error("获取缓存对象失败 - key: {}, target type: {}, error: {}", key, clazz.getName(), e.getMessage(), e);
            return null;
        }
    }

    /**
     * 获取缓存字符串
     *
     * @param key 缓存键值
     * @return 缓存键值对应的数据
     */
    public String getCacheString(final String key) {
        try {
            return stringRedisTemplate.opsForValue().get(key);
        } catch (Exception e) {
            log.error("获取缓存字符串失败：{}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * 获取 key 剩余过期时间
     *
     * @param key redis key
     * @return 剩余时间 (秒), -1 表示永不过期，-2 表示键不存在
     */
    public Long ttl(final String key) {
        return stringRedisTemplate.getExpire(key, TimeUnit.SECONDS);
    }

    /**
     * 删除单个对象
     *
     * @param key
     * @return
     */
    public boolean deleteObject(final String key) {
        return Boolean.TRUE.equals(stringRedisTemplate.delete(key));
    }

    /**
     * 删除集合对象
     *
     * @param collection 多个对象
     * @return 删除数量
     */
    public long deleteObject(final Collection<String> collection) {
        Long count = stringRedisTemplate.delete(collection);
        return count == null ? 0 : count;
    }

    /**
     * 原子递增
     *
     * @param key   redis key
     * @param delta 递增量
     * @return 递增后的值
     */
    public Long increment(final String key, final long delta) {
        return stringRedisTemplate.opsForValue().increment(key, delta);
    }

    /**
     * Lua 脚本执行
     *
     * @param key  Redis key
     * @param limit 限制值
     * @return 执行结果
     */
    public Long luaIncrKey(String key, Integer limit) {
        DefaultRedisScript<Long> redisScript = new DefaultRedisScript<>(buildLuaIncrKeyScript(), Long.class);
        return stringRedisTemplate.execute(redisScript, Collections.singletonList(key), String.valueOf(limit));
    }

    /**
     * 原子预扣减库存 Lua 脚本
     * 如果 key 不存在返回 -1；如果库存不足返回 0（当前库存值，必定 < deductNum）；扣减成功返回剩余库存(>=0)
     */
    private static final String LUA_STOCK_DEDUCT_SCRIPT = """
            local key = KEYS[1]
            local deductNum = tonumber(ARGV[1])
            local current = redis.call('get', key)
            if current == false then
                return -1
            end
            current = tonumber(current)
            if current < deductNum then
                return current
            end
            redis.call('decrby', key, deductNum)
            return current - deductNum
            """;

    /**
     * 原子预扣减库存
     *
     * @param key        库存 key
     * @param deductNum  扣减数量
     * @return -1=key不存在需初始化; >=0且<deductNum=库存不足; >=deductNum=扣减成功(返回剩余库存)
     */
    public long deductStock(String key, int deductNum) {
        DefaultRedisScript<Long> redisScript = new DefaultRedisScript<>(LUA_STOCK_DEDUCT_SCRIPT, Long.class);
        Long result = stringRedisTemplate.execute(redisScript, Collections.singletonList(key), String.valueOf(deductNum));
        return result != null ? result : -1;
    }

    /**
     * 回滚库存（将预扣减的库存加回）
     *
     * @param key    库存 key
     * @param addNum 回滚数量
     * @return 回滚后的库存值
     */
    public long rollbackStock(String key, int addNum) {
        Long result = stringRedisTemplate.opsForValue().increment(key, addNum);
        return result != null ? result : 0;
    }

    /**
     * 初始化库存到 Redis（仅当 key 不存在时设置）
     *
     * @param key      库存 key
     * @param stock    库存数量
     * @param ttlSeconds 过期时间(秒)
     * @return true=初始化成功; false=key已存在
     */
    public boolean initStockIfAbsent(String key, int stock, long ttlSeconds) {
        return Boolean.TRUE.equals(stringRedisTemplate.opsForValue().setIfAbsent(key, String.valueOf(stock), ttlSeconds, TimeUnit.SECONDS));
    }

    /**
     * 设置库存到 Redis（强制覆盖）
     *
     * @param key      库存 key
     * @param stock    库存数量
     * @param ttlSeconds 过期时间(秒)
     */
    public void setStock(String key, int stock, long ttlSeconds) {
        stringRedisTemplate.opsForValue().set(key, String.valueOf(stock), ttlSeconds, TimeUnit.SECONDS);
    }

    /**
     * 扫描匹配的 key
     *
     * @param pattern 匹配规则
     * @return key 集合
     */
    public Set<String> scan(String pattern) {
        return stringRedisTemplate.keys(pattern);
    }
}