package com.example.community.concurrency;

import com.example.community.post.dto.request.PostUpdateRequest;
import com.example.community.post.service.PostService;
import com.example.community.postlike.service.PostLikeService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.*;
import java.util.concurrent.*;

/**
 * 쓰기 경합 측정: 같은 게시글에 좋아요 100건과 게시글 수정 요청 10건을 동시에 보낸다.
 * 커넥션 풀 대기가 락 대기를 가리지 않도록 풀을 120 으로 키웠다(이 테스트 한정).
 * 수정 요청의 소요 시간(락 대기 포함)과 실패 여부를 기록한다.
 */
@TestPropertySource(properties = "spring.datasource.hikari.maximum-pool-size=120")
@Tag("benchmark")
class WriteContentionBenchmarkTest extends ConcurrencyTestSupport {

    static final int LIKES = 100, EDITS = 10, REPS = 5;

    @Autowired PostLikeService likeService;
    @Autowired PostService postService;

    private PostUpdateRequest req(String t) {
        PostUpdateRequest r = new PostUpdateRequest();
        ReflectionTestUtils.setField(r, "title", t);
        ReflectionTestUtils.setField(r, "content", "edited");
        return r;
    }

    @Test
    void likesVsEdits() throws Exception {
        String mode = statsMode() ? "B" : "A";
        for (int rep = 1; rep <= REPS; rep++) {
            List<Long> users = createUsers(LIKES + 1);
            Long author = users.get(0);
            Long postId = createPost(author);

            int total = LIKES + EDITS;
            ExecutorService pool = Executors.newFixedThreadPool(total);
            CountDownLatch ready = new CountDownLatch(total), start = new CountDownLatch(1);
            List<Future<long[]>> likeF = new ArrayList<>(), editF = new ArrayList<>();
            for (int i = 1; i <= LIKES; i++) {
                Long uid = users.get(i);
                likeF.add(pool.submit(() -> timed(ready, start, () -> likeService.like(postId, uid))));
            }
            for (int i = 0; i < EDITS; i++) {
                String title = "edit " + i;
                editF.add(pool.submit(() -> timed(ready, start, () -> postService.updatePost(postId, author, req(title)))));
            }
            ready.await();
            long t0 = System.nanoTime();
            start.countDown();
            List<Double> editMs = new ArrayList<>(), likeMs = new ArrayList<>();
            int editFail = 0, likeFail = 0;
            for (Future<long[]> f : editF) { long[] r = f.get(); if (r[1] == 0) editFail++; editMs.add(r[0] / 1e6); }
            for (Future<long[]> f : likeF) { long[] r = f.get(); if (r[1] == 0) likeFail++; likeMs.add(r[0] / 1e6); }
            double totalMs = (System.nanoTime() - t0) / 1e6;
            pool.shutdown();
            Collections.sort(editMs); Collections.sort(likeMs);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("mode", mode); m.put("rep", rep);
            m.put("edit_p50_ms", r2(editMs.get(EDITS / 2))); m.put("edit_max_ms", r2(editMs.get(EDITS - 1)));
            m.put("edit_fail", editFail);
            m.put("like_p50_ms", r2(likeMs.get(LIKES / 2))); m.put("like_p95_ms", r2(likeMs.get((int) (LIKES * 0.95) - 1)));
            m.put("like_fail", likeFail); m.put("like_count", likeCount(postId)); m.put("total_ms", r2(totalMs));
            record("bench_contention", m);
        }
    }

    private double r2(double d) { return Math.round(d * 100) / 100.0; }

    /** @return {소요 나노초, 성공 여부(1/0)} */
    private long[] timed(CountDownLatch ready, CountDownLatch start, Runnable r) throws Exception {
        ready.countDown(); start.await();
        long s = System.nanoTime();
        try { r.run(); return new long[]{System.nanoTime() - s, 1}; }
        catch (Throwable e) { return new long[]{System.nanoTime() - s, 0}; }
    }
}
