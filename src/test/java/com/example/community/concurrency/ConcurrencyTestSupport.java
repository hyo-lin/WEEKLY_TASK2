package com.example.community.concurrency;

import com.example.community.post.model.Post;
import com.example.community.post.repository.PostRepository;
import com.example.community.user.model.User;
import com.example.community.user.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

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
@SpringBootTest
@ActiveProfiles("test")
abstract class ConcurrencyTestSupport {

    static final GenericContainer<?> MYSQL = new GenericContainer<>("mysql:8.0")
            .withEnv("MYSQL_ROOT_PASSWORD", "test")
            .withEnv("MYSQL_DATABASE", "community")
            .withExposedPorts(3306)
            .waitingFor(Wait.forLogMessage(".*ready for connections.*port: 3306.*\\s", 1)
                    .withStartupTimeout(java.time.Duration.ofMinutes(3)));

    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7")
            .withExposedPorts(6379);

    static {
        MYSQL.start();
        REDIS.start();
    }

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", () -> "jdbc:mysql://" + MYSQL.getHost() + ":" + MYSQL.getMappedPort(3306)
                + "/community?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC");
        r.add("spring.datasource.username", () -> "root");
        r.add("spring.datasource.password", () -> "test");
        r.add("spring.data.redis.host", REDIS::getHost);
        r.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        // 백그라운드 스케줄러가 측정에 끼어들지 않도록 사실상 비활성화 (sync()는 테스트가 직접 호출)
        r.add("scheduler.sync.fixed-delay", () -> "86400000");
    }

    @Autowired protected UserRepository userRepository;
    @Autowired protected PostRepository postRepository;
    @Autowired protected JdbcTemplate jdbc;

    private static final AtomicInteger SEQ = new AtomicInteger();

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
        return postRepository.save(Post.create(author, "title", "content")).getId();
    }

    /** 모든 작업이 latch 에서 동시에 출발하도록 실행하고, 예외 클래스명(성공은 "OK")별 개수를 돌려준다. */
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
        start.countDown();
        Map<String, Integer> dist = new TreeMap<>();
        for (Future<String> f : futures) dist.merge(f.get(120, TimeUnit.SECONDS), 1, Integer::sum);
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
