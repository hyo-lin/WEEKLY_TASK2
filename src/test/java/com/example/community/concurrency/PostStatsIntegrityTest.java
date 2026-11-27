package com.example.community.concurrency;

import com.example.community.post.dto.request.PostCreateRequest;
import com.example.community.post.dto.response.PostCursorResponse;
import com.example.community.post.dto.response.PostResponse;
import com.example.community.post.service.PostService;
import com.example.community.postlike.service.PostLikeService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** post_stats 의 생성 시점 보장, 카운터 행이 없는 데이터(마이그레이션 누락 등)에서의 읽기·쓰기 동작을 검증한다. */
class PostStatsIntegrityTest extends ConcurrencyTestSupport {

    @Autowired PostService postService;
    @Autowired PostLikeService likeService;
    @Autowired PlatformTransactionManager txManager;

    private PostCreateRequest createReq(String title) {
        PostCreateRequest r = new PostCreateRequest();
        ReflectionTestUtils.setField(r, "title", title);
        ReflectionTestUtils.setField(r, "content", "content");
        return r;
    }

    @Test
    void createPostCreatesStatsRowInSameTransaction() {
        Long author = createUsers(1).get(0);
        PostResponse res = postService.createPost(author, createReq("생성 확인"));

        assertThat(intQuery("select count(*) from post_stats where post_id=?", res.getPostId())).isEqualTo(1);
        assertThat(likeCount(res.getPostId())).isZero();
        assertThat(commentCount(res.getPostId())).isZero();
        assertThat(res.getLikeCount()).isZero();

        // 같은 트랜잭션인지: 바깥 트랜잭션을 롤백하면 post 와 post_stats 가 함께 사라져야 한다
        int postsBefore = intQuery("select count(*) from post");
        int statsBefore = intQuery("select count(*) from post_stats");
        new TransactionTemplate(txManager).executeWithoutResult(s -> {
            postService.createPost(author, createReq("롤백 확인"));
            s.setRollbackOnly();
        });
        assertThat(intQuery("select count(*) from post")).isEqualTo(postsBefore);
        assertThat(intQuery("select count(*) from post_stats")).isEqualTo(statsBefore);
    }

    @Test
    void createdPostAppearsInListWithZeroCounters() {
        Long author = createUsers(1).get(0);
        PostResponse res = postService.createPost(author, createReq("목록 확인"));
        PostCursorResponse list = postService.getPosts(author, res.getPostId() + 1, 10);
        assertThat(list.getPosts().get(0).getPostId()).isEqualTo(res.getPostId());
        assertThat(list.getPosts().get(0).getLikeCount()).isZero();
    }

    @Test
    void postsWithoutStatsRowStillAppearWithZeroCounters() {
        ensureFulltextIndex();
        Long author = createUsers(1).get(0);
        PostResponse res = postService.createPost(author, createReq("조인누락 테스트"));
        Long postId = res.getPostId();
        jdbc.update("delete from post_stats where post_id=?", postId);      // 카운터 행 누락 상황

        // 목록 (LEFT JOIN FETCH)
        PostCursorResponse list = postService.getPosts(author, postId + 1, 10);
        assertThat(list.getPosts().get(0).getPostId()).isEqualTo(postId);
        assertThat(list.getPosts().get(0).getLikeCount()).isZero();
        assertThat(list.getPosts().get(0).getCommentCount()).isZero();

        // 상세
        PostResponse detail = postService.getPost(author, postId);
        assertThat(detail.getPostId()).isEqualTo(postId);
        assertThat(detail.getLikeCount()).isZero();

        // 검색 (native LEFT JOIN + COALESCE)
        PostCursorResponse found = postService.searchPosts(author, "조인누락", null, 10);
        assertThat(found.getPosts()).extracting(PostResponse::getPostId).contains(postId);
        assertThat(found.getPosts().stream().filter(p -> p.getPostId().equals(postId)).findFirst().orElseThrow().getLikeCount()).isZero();
    }

    @Test
    void likeOnPostWithoutStatsRowFailsLoudlyAndRollsBack() {
        List<Long> users = createUsers(2);
        PostResponse res = postService.createPost(users.get(0), createReq("행 누락 좋아요"));
        Long postId = res.getPostId();
        jdbc.update("delete from post_stats where post_id=?", postId);

        // 조용히 성공한 척하지 않고 예외로 드러나며, post_like INSERT 도 함께 롤백된다
        assertThatThrownBy(() -> likeService.like(postId, users.get(1))).isInstanceOf(IllegalStateException.class);
        assertThat(intQuery("select count(*) from post_like where post_id=?", postId)).isZero();
    }
}
