package com.example.community.concurrency;

import com.example.community.comment.dto.request.CommentCreateRequest;
import com.example.community.comment.model.Comment;
import com.example.community.comment.repository.CommentRepository;
import com.example.community.comment.service.CommentService;
import com.example.community.global.exception.GeneralException;
import com.example.community.global.response.StatusCode;
import com.example.community.post.model.Post;
import com.example.community.postlike.service.PostLikeService;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/** 수정 후 기대값 검증: 모든 정상 요청 성공, 카운터 = 실제 행 수, 예상 밖 예외(=500) 0건. */
class ConcurrencyAfterTest extends ConcurrencyTestSupport {

    static final int N = 100;

    @Autowired PostLikeService likeService;
    @Autowired CommentService commentService;
    @Autowired CommentRepository commentRepository;
    @Autowired PlatformTransactionManager txManager;

    /** 예상한 GeneralException(상태코드별)은 세어서 정상 반환, 그 밖의 예외는 그대로 던져 "500" 으로 집계한다. */
    private Callable<Void> expecting(Map<StatusCode, AtomicInteger> expected, Callable<?> body) {
        return () -> {
            try { body.call(); }
            catch (GeneralException e) { expected.computeIfAbsent(e.getStatusCode(), k -> new AtomicInteger()).incrementAndGet(); }
            return null;
        };
    }

    private CommentCreateRequest commentReq() {
        CommentCreateRequest r = new CommentCreateRequest();
        ReflectionTestUtils.setField(r, "content", "hi");
        return r;
    }

    @RepeatedTest(5)
    void like100() throws Exception {
        List<Long> users = createUsers(N + 1);
        Long postId = createPost(users.get(0));
        List<Callable<Void>> tasks = new ArrayList<>();
        for (int i = 1; i <= N; i++) { Long uid = users.get(i); tasks.add(() -> { likeService.like(postId, uid); return null; }); }
        Map<String, Integer> dist = runConcurrently(tasks);
        int count = likeCount(postId);
        int rows = intQuery("select count(*) from post_like where post_id=?", postId);
        record("after_like", Map.of("ok", dist.getOrDefault("OK", 0), "rows", rows, "like_count", count, "elapsed_ms", lastElapsedMs, "errors", dist.toString()));
        assertThat(dist).containsOnlyKeys("OK");
        assertThat(rows).isEqualTo(N);
        assertThat(count).isEqualTo(N);
    }

    @RepeatedTest(5)
    void comment100() throws Exception {
        List<Long> users = createUsers(N + 1);
        Long postId = createPost(users.get(0));
        List<Callable<Void>> tasks = new ArrayList<>();
        for (int i = 1; i <= N; i++) { Long uid = users.get(i); tasks.add(() -> { commentService.createComment(postId, uid, commentReq()); return null; }); }
        Map<String, Integer> dist = runConcurrently(tasks);
        int count = commentCount(postId);
        int rows = intQuery("select count(*) from comment where post_id=?", postId);
        record("after_comment", Map.of("ok", dist.getOrDefault("OK", 0), "rows", rows, "comment_count", count, "elapsed_ms", lastElapsedMs, "errors", dist.toString()));
        assertThat(dist).containsOnlyKeys("OK");
        assertThat(rows).isEqualTo(N);
        assertThat(count).isEqualTo(N);
    }

    @RepeatedTest(5)
    void duplicateLikeSameUser() throws Exception {
        List<Long> users = createUsers(2);
        Long postId = createPost(users.get(0));
        Map<StatusCode, AtomicInteger> expected = new ConcurrentHashMap<>();
        List<Callable<Void>> tasks = new ArrayList<>();
        for (int i = 0; i < 10; i++) tasks.add(expecting(expected, () -> likeService.like(postId, users.get(1))));
        Map<String, Integer> dist = runConcurrently(tasks);
        int already = expected.getOrDefault(StatusCode.ALREADY_LIKED, new AtomicInteger()).get();
        int count = likeCount(postId);
        record("after_duplicate_like", Map.of("ok", dist.getOrDefault("OK", 0) - already, "already_liked", already, "rows",
                intQuery("select count(*) from post_like where post_id=?", postId), "like_count", count, "errors", dist.toString()));
        assertThat(dist).containsOnlyKeys("OK");            // GeneralException 외 예외(=500) 없음
        assertThat(already).isEqualTo(9);
        assertThat(count).isEqualTo(1);
    }

    @Test
    void unlikeAll100() throws Exception {
        List<Long> users = createUsers(N + 1);
        Long postId = createPost(users.get(0));
        for (int i = 1; i <= N; i++) likeService.like(postId, users.get(i));
        List<Callable<Void>> tasks = new ArrayList<>();
        for (int i = 1; i <= N; i++) { Long uid = users.get(i); tasks.add(() -> { likeService.unlike(postId, uid); return null; }); }
        Map<String, Integer> dist = runConcurrently(tasks);
        assertThat(dist).containsOnlyKeys("OK");
        assertThat(likeCount(postId)).isZero();
        assertThat(intQuery("select count(*) from post_like where post_id=?", postId)).isZero();
    }

    @Test
    void unlikeSameUserConcurrently() throws Exception {
        List<Long> users = createUsers(3);
        Long postId = createPost(users.get(0));
        likeService.like(postId, users.get(1));
        likeService.like(postId, users.get(2));          // 다른 사용자의 좋아요 1개는 남아야 한다
        Map<StatusCode, AtomicInteger> expected = new ConcurrentHashMap<>();
        List<Callable<Void>> tasks = new ArrayList<>();
        for (int i = 0; i < 10; i++) tasks.add(expecting(expected, () -> likeService.unlike(postId, users.get(1))));
        Map<String, Integer> dist = runConcurrently(tasks);
        assertThat(dist).containsOnlyKeys("OK");
        assertThat(expected.get(StatusCode.LIKE_NOT_FOUND).get()).isEqualTo(9);
        assertThat(likeCount(postId)).isEqualTo(1); // 중복 감소 없음
    }

    @RepeatedTest(5)
    void deleteComments100() throws Exception {
        List<Long> users = createUsers(N + 1);
        Long postId = createPost(users.get(0));
        List<Long> commentIds = new ArrayList<>();
        for (int i = 1; i <= N; i++) commentIds.add(commentService.createComment(postId, users.get(i), commentReq()).getCommentId());
        List<Callable<Void>> tasks = new ArrayList<>();
        for (int i = 1; i <= N; i++) { Long uid = users.get(i); Long cid = commentIds.get(i - 1); tasks.add(() -> { commentService.deleteComment(cid, uid); return null; }); }
        Map<String, Integer> dist = runConcurrently(tasks);
        assertThat(dist).containsOnlyKeys("OK");
        assertThat(commentCount(postId)).isZero();
        assertThat(intQuery("select count(*) from comment where post_id=?", postId)).isZero();
    }

    @Test
    void deleteSameCommentConcurrently() throws Exception {
        List<Long> users = createUsers(7);
        Long postId = createPost(users.get(0));
        List<Long> ids = new ArrayList<>();
        for (int i = 1; i <= 5; i++) ids.add(commentService.createComment(postId, users.get(1), commentReq()).getCommentId());
        Map<StatusCode, AtomicInteger> expected = new ConcurrentHashMap<>();
        List<Callable<Void>> tasks = new ArrayList<>();
        for (int i = 0; i < 10; i++) tasks.add(expecting(expected, () -> { commentService.deleteComment(ids.get(0), users.get(1)); return null; }));
        Map<String, Integer> dist = runConcurrently(tasks);
        assertThat(dist).containsOnlyKeys("OK");
        assertThat(expected.get(StatusCode.COMMENT_NOT_FOUND).get()).isEqualTo(9);
        assertThat(commentCount(postId)).isEqualTo(4);
    }

    @Test
    void likeOnDeletedPostIsRejected() {
        List<Long> users = createUsers(2);
        Long postId = createPost(users.get(0));
        jdbc.update("update post set deleted_at = now() where post_id=?", postId);
        org.junit.jupiter.api.Assertions.assertThrows(GeneralException.class, () -> likeService.like(postId, users.get(1)));
        org.junit.jupiter.api.Assertions.assertThrows(GeneralException.class, () -> commentService.createComment(postId, users.get(1), commentReq()));
        assertThat(intQuery("select count(*) from post_like where post_id=?", postId)).isZero();
    }

    /** @DynamicUpdate 검증: 오래된 Post 엔티티로 제목만 수정해도 그 사이 올라간 view_count 를 덮어쓰지 않는다. */
    @Test
    void titleUpdateDoesNotOverwriteViewCount() throws Exception {
        List<Long> users = createUsers(1);
        Long postId = createPost(users.get(0));
        new TransactionTemplate(txManager).executeWithoutResult(s -> {
            Post stale = postRepository.findById(postId).orElseThrow();   // view_count = 0 으로 로드
            try {
                Executors.newSingleThreadExecutor().submit(
                        () -> jdbc.update("update post set view_count = view_count + 7 where post_id=?", postId)).get(30, TimeUnit.SECONDS);
            } catch (Exception e) { throw new RuntimeException(e); }
            stale.update("new title", "new content");                       // 제목만 변경 → 커밋 시 UPDATE
        });
        assertThat(intQuery("select view_count from post where post_id=?", postId)).isEqualTo(7);
    }
}
