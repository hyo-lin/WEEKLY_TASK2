package com.example.community.post.repository;

import com.example.community.post.model.PostStats;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PostStatsRepository extends JpaRepository<PostStats, Long> {

    // 게시글 생성 시 카운터 행 생성 (PK 를 직접 지정하므로 save() 의 SELECT-후-INSERT 를 피한다)
    @Modifying
    @Query(value = "INSERT INTO post_stats (post_id, like_count, comment_count) VALUES (:postId, 0, 0)", nativeQuery = true)
    void create(@Param("postId") Long postId);

    // 카운터는 DB 에서 원자적으로 증감한다. 영향 행 수 0 = 행이 없거나(데이터 이상) 감소 조건 불충족.
    @Modifying
    @Query("UPDATE PostStats s SET s.likeCount = s.likeCount + 1 WHERE s.postId = :id")
    int increaseLikeCount(@Param("id") Long id);

    @Modifying
    @Query("UPDATE PostStats s SET s.likeCount = s.likeCount - 1 WHERE s.postId = :id AND s.likeCount > 0")
    int decreaseLikeCount(@Param("id") Long id);

    @Modifying
    @Query("UPDATE PostStats s SET s.commentCount = s.commentCount + 1 WHERE s.postId = :id")
    int increaseCommentCount(@Param("id") Long id);

    @Modifying
    @Query("UPDATE PostStats s SET s.commentCount = s.commentCount - 1 WHERE s.postId = :id AND s.commentCount > 0")
    int decreaseCommentCount(@Param("id") Long id);

    @Query("SELECT s.likeCount FROM PostStats s WHERE s.postId = :id")
    int findLikeCount(@Param("id") Long id);
}
