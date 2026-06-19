package com.example.community.postview.buffer;

import org.springframework.stereotype.Component;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ViewCountBuffer {

    private final ConcurrentHashMap<Long, Integer> buffer = new ConcurrentHashMap<>();

    public void increment(Long postId) {
        buffer.merge(postId, 1, Integer::sum);
    }

    public int get(Long postId) {
        return buffer.getOrDefault(postId, 0);
    }

    public Map<Long, Integer> drainAll() {
        Map<Long, Integer> snapshot = new HashMap<>();
        buffer.keySet().forEach(postId -> {
            Integer count = buffer.remove(postId);
            if (count != null) snapshot.put(postId, count);
        });
        return snapshot;
    }

    public boolean isEmpty() {
        return buffer.isEmpty();
    }
}