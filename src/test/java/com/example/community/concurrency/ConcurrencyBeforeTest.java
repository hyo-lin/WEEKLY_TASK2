package com.example.community.concurrency;

import com.example.community.comment.dto.request.CommentCreateRequest;
import com.example.community.comment.service.CommentService;
import com.example.community.postlike.service.PostLikeService;
import com.example.community.postview.buffer.ViewCountBuffer;
import com.example.community.postview.scheduler.ViewCountSyncScheduler;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;

/** 수정 전 코드의 동시성 문제를 수치로 기록한다. 판정이 아니라 기록이 목적이므로 assert 는 두지 않는다. */
class ConcurrencyBeforeTest extends ConcurrencyTestSupport {

    static final int N = 100;

    @Autowired PostLikeService postLikeService;
    @Autowired CommentService commentService;
    @Autowired ViewCountSyncScheduler scheduler;
    @Autowired ViewCountBuffer viewCountBuffer;

    @RepeatedTest(5)
    void likeLostUpdate() throws Exception {
        List<Long> users = createUsers(N + 1);
        Long postId = createPost(users.get(0));
        List<Callable<Void>> tasks = new ArrayList<>();
        for (int i = 1; i <= N; i++) {
            Long uid = users.get(i);
            tasks.add(() -> { postLikeService.like(postId, uid); return null; });
        }
        Map<String, Integer> dist = runConcurrently(tasks);
        int likeCount = intQuery("select like_count from post where post_id=?", postId);
        int rows = intQuery("select count(*) from post_like where post_id=?", postId);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("requests", N); m.put("expected", N);
        m.put("like_count", likeCount); m.put("post_like_rows", rows);
        m.put("lost", rows - likeCount); m.put("ok", dist.getOrDefault("OK", 0));
        m.put("errors", dist.toString());
        record("like_lost_update", m);
    }

    @RepeatedTest(5)
    void commentLostUpdate() throws Exception {
        List<Long> users = createUsers(N + 1);
        Long postId = createPost(users.get(0));
        List<Callable<Void>> tasks = new ArrayList<>();
        for (int i = 1; i <= N; i++) {
            Long uid = users.get(i);
            CommentCreateRequest req = new CommentCreateRequest();
            ReflectionTestUtils.setField(req, "content", "hi");
            tasks.add(() -> { commentService.createComment(postId, uid, req); return null; });
        }
        Map<String, Integer> dist = runConcurrently(tasks);
        int commentCount = intQuery("select comment_count from post where post_id=?", postId);
        int rows = intQuery("select count(*) from comment where post_id=?", postId);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("requests", N); m.put("expected", N);
        m.put("comment_count", commentCount); m.put("comment_rows", rows);
        m.put("lost", rows - commentCount); m.put("ok", dist.getOrDefault("OK", 0));
        m.put("errors", dist.toString());
        record("comment_lost_update", m);
    }

    @RepeatedTest(5)
    void duplicateLikeSameUser() throws Exception {
        for (int dup : new int[]{2, 5, 10}) {
            List<Long> users = createUsers(2);
            Long postId = createPost(users.get(0));
            Long uid = users.get(1);
            List<Callable<Void>> tasks = new ArrayList<>();
            for (int i = 0; i < dup; i++) tasks.add(() -> { postLikeService.like(postId, uid); return null; });
            Map<String, Integer> dist = runConcurrently(tasks);
            int likeCount = intQuery("select like_count from post where post_id=?", postId);
            int rows = intQuery("select count(*) from post_like where post_id=?", postId);
            int serverErrors = dist.entrySet().stream()
                    .filter(e -> !e.getKey().equals("OK") && !e.getKey().startsWith("GeneralException"))
                    .mapToInt(Map.Entry::getValue).sum();
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("requests", dup); m.put("ok", dist.getOrDefault("OK", 0));
            m.put("post_like_rows", rows); m.put("like_count", likeCount);
            m.put("would_be_500", serverErrors); m.put("errors", dist.toString());
            record("duplicate_like_" + dup, m);
        }
    }

    @RepeatedTest(5)
    void viewSyncVsLikeOverwrite() throws Exception {
        final int views = 500;
        List<Long> users = createUsers(N + 1);
        Long postId = createPost(users.get(0));
        List<Callable<Void>> tasks = new ArrayList<>();
        for (int i = 1; i <= N; i++) {
            Long uid = users.get(i);
            tasks.add(() -> { postLikeService.like(postId, uid); return null; });
        }
        AtomicBoolean stop = new AtomicBoolean(false);
        // 조회 유입(Redis) + 스케줄러 sync() 를 좋아요와 동시에 실행
        tasks.add(() -> { for (int i = 0; i < views; i++) { viewCountBuffer.increment(postId); if (i % 5 == 0) Thread.sleep(1); } return null; });
        tasks.add(() -> {
            while (!stop.get()) { scheduler.sync(); Thread.sleep(2); }
            return null;
        });
        // 좋아요/조회 유입이 끝나면 sync 루프를 멈추기 위한 감시 태스크
        Thread watcher = new Thread(() -> {
            try {
                long end = System.currentTimeMillis() + 15000;
                while (System.currentTimeMillis() < end) {
                    if (intQuery("select count(*) from post_like where post_id=?", postId) >= N) { Thread.sleep(500); break; }
                    Thread.sleep(50);
                }
            } catch (Exception ignored) {}
            stop.set(true);
        });
        watcher.start();
        Map<String, Integer> dist = runConcurrently(tasks);
        watcher.join();
        scheduler.sync(); // 남은 버퍼 마지막 반영
        int viewCount = intQuery("select view_count from post where post_id=?", postId);
        int likeCount = intQuery("select like_count from post where post_id=?", postId);
        int rows = intQuery("select count(*) from post_like where post_id=?", postId);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("expected_views", views); m.put("view_count", viewCount); m.put("view_lost", views - viewCount);
        m.put("expected_likes", rows); m.put("like_count", likeCount); m.put("like_lost", rows - likeCount);
        m.put("errors", dist.toString());
        record("view_sync_vs_like", m);
    }
}
