package com.example.community.concurrency;

import com.example.community.IntegrationTestBase;
import com.example.community.post.model.Post;
import com.example.community.post.repository.PostRepository;
import com.example.community.user.model.User;
import com.example.community.user.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 동시성 "수정 전" 재현용 통합 테스트 기반 클래스. 실제 MySQL 8.0 / Redis 7 컨테이너를 사용한다.
 * 목적은 pass/fail 판정이 아니라 수치 기록이다. 결과는 build/concurrency-results.jsonl 에 누적된다.
 */
abstract class ConcurrencyTestSupport extends IntegrationTestBase {

    @Autowired protected UserRepository userRepository;
    @Autowired protected PostRepository postRepository;
    @Autowired protected JdbcTemplate jdbc;

    private static final AtomicInteger SEQ = new AtomicInteger();

    private Boolean statsMode;

    /** post_stats 테이블이 있으면 B 방식(카운터 분리) 코드로 실행 중인 것으로 본다. */
    protected boolean statsMode() {
        if (statsMode == null) {
            statsMode = intQuery("select count(*) from information_schema.tables where table_schema='community' and table_name='post_stats'") > 0;
            if (statsMode && intQuery("select count(*) from information_schema.table_constraints where table_schema='community' "
                    + "and table_name='post_stats' and constraint_type='FOREIGN KEY'") == 0) {
                // 운영 마이그레이션 SQL 과 같은 스키마로 맞춘다 (ddl-auto 는 post_stats → post FK 를 만들지 않음)
                jdbc.execute("ALTER TABLE post_stats ADD CONSTRAINT fk_post_stats_post FOREIGN KEY (post_id) REFERENCES post (post_id)");
            }
        }
        return statsMode;
    }

    /** 검색 테스트용 FULLTEXT(ngram) 인덱스. 운영과 같은 방식이며 이미 있으면 건너뛴다. */
    protected void ensureFulltextIndex() {
        if (intQuery("select count(*) from information_schema.statistics where table_schema='community' and table_name='post' and index_name='ft_title'") == 0) {
            jdbc.execute("ALTER TABLE post ADD FULLTEXT INDEX ft_title (title) WITH PARSER ngram");
        }
    }

    protected int likeCount(Long postId) {
        return intQuery(statsMode() ? "select like_count from post_stats where post_id=?" : "select like_count from post where post_id=?", postId);
    }

    protected int commentCount(Long postId) {
        return intQuery(statsMode() ? "select comment_count from post_stats where post_id=?" : "select comment_count from post where post_id=?", postId);
    }

    protected List<Long> createUsers(int n) {
        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            int k = SEQ.incrementAndGet();
            ids.add(userRepository.save(User.create("u" + k + "@t.com", "pw", "nick" + k)).getId());
        }
        return ids;
    }

    protected Long createPost(Long authorId) {
        User author = userRepository.findById(authorId).orElseThrow();
        Long id = postRepository.save(Post.create(author, "title", "content")).getId();
        if (statsMode()) jdbc.update("insert into post_stats(post_id, like_count, comment_count) values (?, 0, 0)", id);
        return id;
    }

    /** 모든 작업이 latch 에서 동시에 출발하도록 실행하고, 예외 클래스명(성공은 "OK")별 개수를 돌려준다. */
    protected volatile long lastElapsedMs;

    protected Map<String, Integer> runConcurrently(List<Callable<Void>> tasks) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
        CountDownLatch ready = new CountDownLatch(tasks.size());
        CountDownLatch start = new CountDownLatch(1);
        List<Future<String>> futures = new ArrayList<>();
        for (Callable<Void> t : tasks) {
            futures.add(pool.submit(() -> {
                ready.countDown();
                start.await();
                try {
                    t.call();
                    return "OK";
                } catch (Throwable e) {
                    return rootName(e);
                }
            }));
        }
        ready.await();
        long t0 = System.nanoTime();
        start.countDown();
        Map<String, Integer> dist = new TreeMap<>();
        for (Future<String> f : futures) dist.merge(f.get(120, TimeUnit.SECONDS), 1, Integer::sum);
        lastElapsedMs = (System.nanoTime() - t0) / 1_000_000;
        pool.shutdown();
        return dist;
    }

    private String rootName(Throwable e) {
        Throwable t = e;
        while (t.getCause() != null && t.getCause() != t) t = t.getCause();
        return e.getClass().getSimpleName() + (t != e ? "<-" + t.getClass().getSimpleName() : "");
    }

    protected int intQuery(String sql, Object... args) {
        Integer v = jdbc.queryForObject(sql, Integer.class, args);
        return v == null ? 0 : v;
    }

    protected void record(String test, Map<String, ?> fields) {
        StringBuilder sb = new StringBuilder("{\"test\":\"").append(test).append("\"");
        fields.forEach((k, v) -> sb.append(",\"").append(k).append("\":")
                .append(v instanceof Number ? v.toString() : "\"" + v + "\""));
        sb.append("}\n");
        System.out.println("[RESULT] " + sb);
        try {
            Path p = Path.of("build", "concurrency-results.jsonl");
            Files.createDirectories(p.getParent());
            Files.writeString(p, sb.toString(), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
