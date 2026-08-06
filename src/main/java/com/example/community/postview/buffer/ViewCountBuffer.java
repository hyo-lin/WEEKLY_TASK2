package com.example.community.postview.buffer;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ViewCountBuffer {

    private final ConcurrentHashMap<Long, Integer> buffer = new ConcurrentHashMap<>();
    private static final String BUFFER_KEY = "view:buffer";
    private final StringRedisTemplate redisTemplate;

    public ViewCountBuffer(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void increment(Long postId) {
        redisTemplate.opsForHash().increment(BUFFER_KEY, postId.toString(), 1);
    }

    public int get(Long postId) {
        Object value = redisTemplate.opsForHash().get(BUFFER_KEY, postId.toString());
        return value != null ? Integer.parseInt((String) value) : 0;
    }

    public Map<Long, Integer> drainAll() {
        String processingKey = BUFFER_KEY + ":processing:" + System.currentTimeMillis();

        Boolean renamed = redisTemplate.renameIfAbsent(BUFFER_KEY, processingKey);
        if (!Boolean.TRUE.equals(renamed)) {
            return Map.of();
        }

        Map<Object, Object> entries = redisTemplate.opsForHash().entries(processingKey);

        Map<Long, Integer> snapshot = new HashMap<>();
        entries.forEach((postId, count) ->
                snapshot.put(Long.valueOf((String) postId), Integer.valueOf((String) count))
        );

        redisTemplate.delete(processingKey);
        return snapshot;
    }

    public boolean isEmpty() {
        Long size = redisTemplate.opsForHash().size(BUFFER_KEY);
        return size == null || size == 0;
    }
}