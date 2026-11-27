package com.example.community.concurrency;

import com.example.community.post.service.PostService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * 조회 비용 측정 (A: 카운터가 post 에 있음 / B: post_stats 분리). 현재 실행 중인 프로덕션 코드 기준으로 측정하며,
 * post_stats 테이블 존재 여부로 모드를 판별한다.
 * 데이터: 게시글 10,000건, 사용자 300명, 게시글당 좋아요 0~20개·댓글 0~5개, 프로필 이미지 보유.
 */
@Tag("benchmark")
class ReadCostBenchmarkTest extends ConcurrencyTestSupport {

    static final int POSTS = 10_000, USERS = 300, ITER = 100, WARMUP = 30, ROUNDS = 3;

    @Autowired PostService postService;

    private void seed() {
        List<Long> users = createUsers(USERS);
        for (Long u : users) jdbc.update("insert into profile_image(user_id, image_url, created_at) values (?, ?, now())", u, "https://x/p" + u + ".png");
        boolean stats = statsMode();
        Long maxBefore = jdbc.queryForObject("select coalesce(max(post_id), 0) from post", Long.class); // 같은 DB 에 다른 테스트의 데이터가 있어도 시드분만 다룬다
        Random rnd = new Random(42);
        List<Object[]> posts = new ArrayList<>();
        int[] likes = new int[POSTS], comments = new int[POSTS];
        for (int i = 0; i < POSTS; i++) {
            likes[i] = rnd.nextInt(21); comments[i] = rnd.nextInt(6);
            Long author = users.get(rnd.nextInt(USERS));
            posts.add(stats
                    ? new Object[]{author, "러닝 모임 " + i, "content " + i, 0}
                    : new Object[]{author, "러닝 모임 " + i, "content " + i, 0, likes[i], comments[i]});
        }
        if (stats) jdbc.batchUpdate("insert into post(user_id, title, content, view_count, created_at) values (?,?,?,?, now())", posts);
        else jdbc.batchUpdate("insert into post(user_id, title, content, view_count, like_count, comment_count, created_at) values (?,?,?,?,?,?, now())", posts);
        List<Long> ids = jdbc.queryForList("select post_id from post where post_id > ? order by post_id", Long.class, maxBefore);
        if (stats) {
            List<Object[]> st = new ArrayList<>();
            for (int i = 0; i < POSTS; i++) st.add(new Object[]{ids.get(i), likes[i], comments[i]});
            jdbc.batchUpdate("insert into post_stats(post_id, like_count, comment_count) values (?,?,?)", st);
        }
        List<Object[]> lk = new ArrayList<>(), cm = new ArrayList<>();
        for (int i = 0; i < POSTS; i++) {
            List<Long> pool = new ArrayList<>(users); Collections.shuffle(pool, rnd);
            for (int j = 0; j < likes[i]; j++) lk.add(new Object[]{ids.get(i), pool.get(j)});
            for (int j = 0; j < comments[i]; j++) cm.add(new Object[]{ids.get(i), pool.get(j), "comment"});
        }
        jdbc.batchUpdate("insert into post_like(post_id, user_id) values (?,?)", lk);
        jdbc.batchUpdate("insert into comment(post_id, user_id, content, created_at) values (?,?,?, now())", cm);
        ensureFulltextIndex();
        jdbc.execute("ANALYZE TABLE post, post_like, comment" + (stats ? ", post_stats" : ""));
        System.out.println("[BENCH] seeded posts=" + POSTS + " likes=" + lk.size() + " comments=" + cm.size());
    }

    private double pct(long[] sortedNanos, double p) { return sortedNanos[(int) Math.min(sortedNanos.length - 1, Math.ceil(p * sortedNanos.length) - 1)] / 1e6; }

    private void measure(String mode, int round, String op, Runnable r) {
        for (int i = 0; i < WARMUP; i++) r.run();
        long[] t = new long[ITER];
        for (int i = 0; i < ITER; i++) { long s = System.nanoTime(); r.run(); t[i] = System.nanoTime() - s; }
        Arrays.sort(t);
        record("bench_read", new LinkedHashMap<>(Map.of("mode", mode, "round", round, "op", op,
                "p50_ms", Math.round(pct(t, 0.50) * 100) / 100.0, "p95_ms", Math.round(pct(t, 0.95) * 100) / 100.0)));
    }

    @Test
    void readCost() throws Exception {
        seed();
        String mode = statsMode() ? "B" : "A";
        Long viewer = userRepository.findAll().get(0).getId();
        Long maxId = jdbc.queryForObject("select max(post_id) from post", Long.class);
        Long detailId = maxId - 100;
        explain(mode);
        for (int round = 1; round <= ROUNDS; round++) {
            measure(mode, round, "list10", () -> postService.getPosts(viewer, null, 10));
            measure(mode, round, "list50", () -> postService.getPosts(viewer, null, 50));
            measure(mode, round, "list10_deep", () -> postService.getPosts(viewer, maxId - 5_000, 10));
            measure(mode, round, "detail", () -> postService.getPost(viewer, detailId));
            measure(mode, round, "search10", () -> postService.searchPosts(viewer, "러닝", null, 10));
        }
    }

    private void explain(String mode) throws IOException {
        StringBuilder sb = new StringBuilder();
        String list = statsMode()
                ? "SELECT p.post_id, p.title, COALESCE(s.like_count,0), COALESCE(s.comment_count,0) FROM post p LEFT JOIN post_stats s ON s.post_id = p.post_id WHERE p.deleted_at IS NULL AND p.post_id < 5000 ORDER BY p.post_id DESC LIMIT 11"
                : "SELECT p.post_id, p.title, p.like_count, p.comment_count FROM post p WHERE p.deleted_at IS NULL AND p.post_id < 5000 ORDER BY p.post_id DESC LIMIT 11";
        String search = statsMode()
                ? "SELECT p.post_id, u.nickname, COALESCE(s.like_count,0), COALESCE(s.comment_count,0) FROM post p JOIN user u ON p.user_id=u.user_id LEFT JOIN post_stats s ON s.post_id=p.post_id WHERE p.deleted_at IS NULL AND MATCH(p.title) AGAINST('러닝' IN BOOLEAN MODE) ORDER BY p.post_id DESC LIMIT 11"
                : "SELECT p.post_id, u.nickname, p.like_count, p.comment_count FROM post p JOIN user u ON p.user_id=u.user_id WHERE p.deleted_at IS NULL AND MATCH(p.title) AGAINST('러닝' IN BOOLEAN MODE) ORDER BY p.post_id DESC LIMIT 11";
        for (String q : new String[]{list, search}) {
            sb.append("-- ").append(q).append("\n");
            for (Map<String, Object> row : jdbc.queryForList("EXPLAIN " + q)) sb.append(row).append("\n");
            sb.append("-- TREE\n");
            for (Map<String, Object> row : jdbc.queryForList("EXPLAIN FORMAT=TREE " + q)) sb.append(row.values().iterator().next()).append("\n");
            sb.append("\n");
        }
        Files.writeString(Path.of("build", "explain-" + mode + ".txt"), sb.toString());
    }
}
