package com.example.community.postview.buffer;

import org.springframework.stereotype.Component;
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

    public ConcurrentHashMap<Long, Integer> getAll() {
        return buffer;
    }

    public void clear(Long postId) {
        buffer.remove(postId);
    }

    public boolean isEmpty() {
        return buffer.isEmpty();
    }
}