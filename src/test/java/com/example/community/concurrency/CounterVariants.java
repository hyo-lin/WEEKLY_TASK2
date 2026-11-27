package com.example.community.concurrency;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Map;

/**
 * 카운터 갱신 방식 비교용 구현 (테스트 전용). 프로덕션 코드가 아니다.
 * 모든 방식이 같은 SQL 을 쓰도록 JdbcTemplate 으로 통일해 "락 순서/대상"만 달라지게 했다.
 */
class CounterVariants {

    enum Variant { BASELINE, A_LOCK_ORDER, B_STATS_TABLE, C_REDIS }
    enum Kind {
        LIKE("like_count", "post_like"), COMMENT("comment_count", "comment");
        final String col, table;
        Kind(String col, String table) { this.col = col; this.table = table; }
    }

    static class AlreadyLiked extends RuntimeException {}

    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final StringRedisTemplate redis;

    CounterVariants(JdbcTemplate jdbc, TransactionTemplate tx, StringRedisTemplate redis) {
        this.jdbc = jdbc; this.tx = tx; this.redis = redis;
    }

    static String bufferKey(Kind k) { return "cmp:buffer:" + k.col; }

    void add(Variant v, Kind k, long postId, long userId) {
        switch (v) {
            case BASELINE -> tx.executeWithoutResult(s -> {          // 현재 순서: INSERT(FK 공유락) → UPDATE
                checkExists(k, postId, userId);
                insert(k, postId, userId);
                jdbc.update("update post set " + k.col + " = " + k.col + " + 1 where post_id = ?", postId);
            });
            case A_LOCK_ORDER -> tx.executeWithoutResult(s -> {      // UPDATE(배타락) 먼저 → INSERT
                checkExists(k, postId, userId);
                jdbc.update("update post set " + k.col + " = " + k.col + " + 1 where post_id = ?", postId);
                insert(k, postId, userId);
            });
            case B_STATS_TABLE -> tx.executeWithoutResult(s -> {     // INSERT(post 공유락) → post_stats 갱신(다른 행)
                checkExists(k, postId, userId);
                insert(k, postId, userId);
                jdbc.update("update post_stats set " + k.col + " = " + k.col + " + 1 where post_id = ?", postId);
            });
            case C_REDIS -> {                                        // DB 는 INSERT 만, 카운터는 커밋 후 Redis HINCRBY
                tx.executeWithoutResult(s -> {
                    checkExists(k, postId, userId);
                    insert(k, postId, userId);
                });
                redis.opsForHash().increment(bufferKey(k), String.valueOf(postId), 1);
            }
        }
    }

    private void checkExists(Kind k, long postId, long userId) {
        if (k == Kind.LIKE) {
            Integer c = jdbc.queryForObject("select count(*) from post_like where post_id=? and user_id=?",
                    Integer.class, postId, userId);
            if (c != null && c > 0) throw new AlreadyLiked();
        }
    }

    private void insert(Kind k, long postId, long userId) {
        if (k == Kind.LIKE) jdbc.update("insert into post_like(post_id, user_id) values (?, ?)", postId, userId);
        else jdbc.update("insert into comment(post_id, user_id, content, created_at) values (?, ?, 'hi', now())", postId, userId);
    }

    /** C 방식: 기존 ViewCountBuffer.drainAll 과 같은 rename → 읽기 → DB 반영 패턴 */
    void flushRedis(Kind k) {
        String key = bufferKey(k), processing = key + ":processing";
        if (!Boolean.TRUE.equals(redis.renameIfAbsent(key, processing))) return;
        Map<Object, Object> entries = redis.opsForHash().entries(processing);
        tx.executeWithoutResult(s -> entries.forEach((pid, n) ->
                jdbc.update("update post set " + k.col + " = " + k.col + " + ? where post_id = ?",
                        Integer.valueOf((String) n), Long.valueOf((String) pid))));
        redis.delete(processing);
    }

    int bufferValue(Kind k, long postId) {
        Object o = redis.opsForHash().get(bufferKey(k), String.valueOf(postId));
        return o == null ? 0 : Integer.parseInt((String) o);
    }
}
