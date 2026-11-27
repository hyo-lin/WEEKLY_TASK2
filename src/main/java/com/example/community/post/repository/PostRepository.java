package com.example.community.post.repository;

import com.example.community.post.dto.projection.PostSearchProjection;
import com.example.community.post.model.Post;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PostRepository extends JpaRepository<Post, Long> {

    @Query("SELECT p FROM Post p LEFT JOIN FETCH p.stats WHERE p.deletedAt IS NULL AND (:cursor IS NULL OR p.id < :cursor) ORDER BY p.id DESC")
    Slice<Post> findAllActive(@Param("cursor") Long cursor, Pageable pageable);

    // FULLTEXT 검색 (native query + Projection)
    @Query(value = "SELECT p.post_id as postId, u.user_id as userId, u.nickname as nickname, " +
            "p.title as title, p.view_count as viewCount, COALESCE(s.like_count, 0) as likeCount, " +
            "COALESCE(s.comment_count, 0) as commentCount, p.created_at as createdAt " +
            "FROM post p " +
            "JOIN user u ON p.user_id = u.user_id " +
            "LEFT JOIN post_stats s ON s.post_id = p.post_id " +
            "WHERE p.deleted_at IS NULL " +
            "AND (:cursor IS NULL OR p.post_id < :cursor) " +
            "AND MATCH(p.title) AGAINST(:keyword IN BOOLEAN MODE) " +
            "ORDER BY p.post_id DESC " +
            "LIMIT :size",
            nativeQuery = true)
    List<PostSearchProjection> searchByFulltext(@Param("keyword") String keyword,
                                                @Param("cursor") Long cursor,
                                                @Param("size") int size);

    // 카운터(post_stats)를 함께 읽는다. LEFT JOIN 이라 카운터 행이 없는 게시글도 사라지지 않고 카운터 0 으로 보인다.
    @Query("SELECT p FROM Post p LEFT JOIN FETCH p.stats WHERE p.id = :id AND p.deletedAt IS NULL")
    Optional<Post> findByIdAndDeletedAtIsNull(@Param("id") Long id);

    boolean existsByIdAndDeletedAtIsNull(Long id);
}
