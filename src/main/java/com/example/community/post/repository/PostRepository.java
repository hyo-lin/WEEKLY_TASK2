package com.example.community.post.repository;

import com.example.community.post.dto.projection.PostSearchProjection;
import com.example.community.post.model.Post;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PostRepository extends JpaRepository<Post, Long> {

    @Query("SELECT p FROM Post p WHERE p.deletedAt IS NULL AND (:cursor IS NULL OR p.id < :cursor) ORDER BY p.id DESC")
    Slice<Post> findAllActive(@Param("cursor") Long cursor, Pageable pageable);

    // FULLTEXT 검색 (native query + Projection)
    @Query(value = "SELECT p.post_id as postId, u.user_id as userId, u.nickname as nickname, " +
            "p.title as title, p.view_count as viewCount, p.like_count as likeCount, " +
            "p.comment_count as commentCount, p.created_at as createdAt " +
            "FROM post p " +
            "JOIN user u ON p.user_id = u.user_id " +
            "WHERE p.deleted_at IS NULL " +
            "AND (:cursor IS NULL OR p.post_id < :cursor) " +
            "AND MATCH(p.title) AGAINST(:keyword IN BOOLEAN MODE) " +
            "ORDER BY p.post_id DESC " +
            "LIMIT :size",
            nativeQuery = true)
    List<PostSearchProjection> searchByFulltext(@Param("keyword") String keyword,
                                                @Param("cursor") Long cursor,
                                                @Param("size") int size);

    Optional<Post> findByIdAndDeletedAtIsNull(Long id);
}