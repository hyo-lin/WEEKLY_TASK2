package com.example.community.concurrency;

import com.example.community.concurrency.CounterVariants.Kind;
import com.example.community.concurrency.CounterVariants.Variant;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.*;
import java.util.concurrent.Callable;

/**
 * 비교 하네스(코드가 A 방식이던 시점의 측정용). 프로덕션이 B(post_stats)로 바뀐 뒤에는 테이블이 충돌하므로 비활성화했다.
 * 결과는 docs/portfolio/02-concurrency-after.md 에 기록되어 있다.
 */
@Disabled("비교 측정용 (결과는 docs/portfolio/02 에 기록). 프로덕션 코드가 post_stats 를 쓰므로 비활성화")
/** 카운터 갱신 방식별(기준선/A/B/C) 동시 100건 측정. 결과는 build/concurrency-results.jsonl 에 variant_* 로 기록. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class VariantComparisonTest extends ConcurrencyTestSupport {

    static final int N = 100, REPS = 5;

    @Autowired PlatformTransactionManager txManager;
    @Autowired StringRedisTemplate redis;
    CounterVariants variants;

    @BeforeAll
    void setUp() {
        variants = new CounterVariants(jdbc, new TransactionTemplate(txManager), redis);
        jdbc.execute("create table if not exists post_stats (post_id bigint primary key, " +
                "like_count int not null default 0, comment_count int not null default 0, " +
                "foreign key (post_id) references post(post_id))");
    }

    @AfterAll
    void tearDown() { jdbc.execute("drop table if exists post_stats"); }

    @Test void baseline() throws Exception { measure(Variant.BASELINE); }
    @Test void a_lockOrder() throws Exception { measure(Variant.A_LOCK_ORDER); }
    @Test void b_statsTable() throws Exception { measure(Variant.B_STATS_TABLE); }
    @Test void c_redis() throws Exception { measure(Variant.C_REDIS); }

    void measure(Variant v) throws Exception {
        for (Kind k : Kind.values()) for (int rep = 1; rep <= REPS; rep++) concurrentInsert(v, k, rep);
        for (int rep = 1; rep <= REPS; rep++) duplicateLike(v, 10, rep);
    }

    private Long newPost(Long author) {
        Long postId = createPost(author);
        jdbc.update("insert into post_stats(post_id) values (?)", postId);
        return postId;
    }

    private void concurrentInsert(Variant v, Kind k, int rep) throws Exception {
        List<Long> users = createUsers(N + 1);
        Long postId = newPost(users.get(0));
        List<Callable<Void>> tasks = new ArrayList<>();
        for (int i = 1; i <= N; i++) { Long uid = users.get(i); tasks.add(() -> { variants.add(v, k, postId, uid); return null; }); }
        Map<String, Integer> dist = runConcurrently(tasks);
        long elapsed = lastElapsedMs;

        int rows = intQuery("select count(*) from " + k.table + " where post_id=?", postId);
        int beforeFlush, afterFlush;
        switch (v) {
            case B_STATS_TABLE -> beforeFlush = afterFlush = intQuery("select " + k.col + " from post_stats where post_id=?", postId);
            case C_REDIS -> {
                int db = intQuery("select " + k.col + " from post where post_id=?", postId);
                beforeFlush = db + variants.bufferValue(k, postId);   // 조회 시 DB + 버퍼 합산값
                int dbOnly = db;
                variants.flushRedis(k);
                afterFlush = intQuery("select " + k.col + " from post where post_id=?", postId);
                record0(v, k, rep, dist, rows, beforeFlush, afterFlush, dbOnly, elapsed);
                return;
            }
            default -> beforeFlush = afterFlush = intQuery("select " + k.col + " from post where post_id=?", postId);
        }
        record0(v, k, rep, dist, rows, beforeFlush, afterFlush, -1, elapsed);
    }

    private void record0(Variant v, Kind k, int rep, Map<String, Integer> dist, int rows,
                         int counterBefore, int counterAfter, int dbOnlyBeforeFlush, long elapsed) {
        int deadlock = 0, other = 0;
        for (var e : dist.entrySet()) {
            if (e.getKey().equals("OK") || e.getKey().contains("AlreadyLiked") || e.getKey().contains("DuplicateKey")) continue;
            if (e.getKey().contains("CannotAcquireLock") || e.getKey().contains("Deadlock")) deadlock += e.getValue();
            else other += e.getValue();
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("variant", v.name()); m.put("kind", k.name()); m.put("rep", rep);
        m.put("requests", N); m.put("ok", dist.getOrDefault("OK", 0));
        m.put("rows", rows); m.put("deadlock", deadlock); m.put("other_err", other);
        m.put("counter_before_flush", counterBefore); m.put("counter_after_flush", counterAfter);
        m.put("db_only_before_flush", dbOnlyBeforeFlush);
        m.put("mismatch", rows - counterAfter); m.put("elapsed_ms", elapsed);
        m.put("errors", dist.toString());
        record("variant_concurrent", m);
    }

    private void duplicateLike(Variant v, int dup, int rep) throws Exception {
        List<Long> users = createUsers(2);
        Long postId = newPost(users.get(0));
        Long uid = users.get(1);
        List<Callable<Void>> tasks = new ArrayList<>();
        for (int i = 0; i < dup; i++) tasks.add(() -> { variants.add(v, Kind.LIKE, postId, uid); return null; });
        Map<String, Integer> dist = runConcurrently(tasks);
        int rows = intQuery("select count(*) from post_like where post_id=?", postId);
        if (v == Variant.C_REDIS) variants.flushRedis(Kind.LIKE);
        String col = v == Variant.B_STATS_TABLE ? "select like_count from post_stats where post_id=?" : "select like_count from post where post_id=?";
        int counter = intQuery(col, postId);
        int already = 0, deadlock = 0, other = 0;
        for (var e : dist.entrySet()) {
            if (e.getKey().equals("OK")) continue;
            if (e.getKey().contains("AlreadyLiked") || e.getKey().contains("DuplicateKey")) already += e.getValue();
            else if (e.getKey().contains("CannotAcquireLock")) deadlock += e.getValue();
            else other += e.getValue();
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("variant", v.name()); m.put("rep", rep); m.put("requests", dup);
        m.put("ok", dist.getOrDefault("OK", 0)); m.put("already", already); m.put("deadlock", deadlock);
        m.put("other_err", other); m.put("rows", rows); m.put("counter", counter);
        m.put("errors", dist.toString());
        record("variant_duplicate_like", m);
    }
}
