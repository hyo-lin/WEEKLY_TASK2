package com.example.community.postview.service;

import com.example.community.postview.repository.PostViewRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ViewCountService {

    private final RedisTemplate<String, String> redisTemplate;
    private final PostViewRepository postViewRepository;

    @Value("${redis.view-count.key-pattern}")
    private String countKeyPattern;

    @Value("${redis.view-count.ttl-days}")
    private long ttlDays;

    /** 조회할 때마다 무조건 +1 */
    public void increment(Long postId) {
        String key = String.format(countKeyPattern, postId);
        redisTemplate.opsForValue().increment(key);
        redisTemplate.expire(key, ttlDays, TimeUnit.DAYS);  // 스케줄러 주기보다 충분히 길게
    }

    /** 단건: Redis + DB 합산 조회수 (상세용) */
    public int getViewCount(Long postId) {
        return getCachedCount(postId)
                + (int) postViewRepository.countByPostId(postId);
    }

    /** 단건: Redis 미동기화 카운트만 */
    public int getCachedCount(Long postId) {
        String val = redisTemplate.opsForValue()
                .get(String.format(countKeyPattern, postId));
        return val != null ? Integer.parseInt(val) : 0;  // Long → int
    }

    /** 다건: Redis 미동기화 카운트 한 번에 조회 (목록용) */
    public Map<Long, Integer> getCachedCounts(List<Long> postIds) {  // Long → Integer
        List<String> keys = postIds.stream()
                .map(id -> String.format(countKeyPattern, id))
                .collect(Collectors.toList());

        List<String> values = redisTemplate.opsForValue().multiGet(keys);

        Map<Long, Integer> result = new HashMap<>();
        for (int i = 0; i < postIds.size(); i++) {
            String val = values.get(i);
            result.put(postIds.get(i), val != null ? Integer.parseInt(val) : 0);
        }
        return result;
    }

    /** 스케줄러용 */
    public Set<String> getCountKeys() {
        String searchPattern = countKeyPattern.replace("%d", "*");
        return redisTemplate.keys(searchPattern);
    }

    public void deleteCountKey(String key) {
        redisTemplate.delete(key);
    }
}
