package com.example.community.post.repository;

import com.example.community.post.model.Post;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PostRepository extends JpaRepository<Post, Long> {

    @Query("SELECT p FROM Post p WHERE p.deletedAt IS NULL AND (:cursor IS NULL OR p.id < :cursor) ORDER BY p.id DESC")
    Slice<Post> findAllActive(@Param("cursor") Long cursor, Pageable pageable);

    @Query("SELECT p FROM Post p WHERE p.deletedAt IS NULL AND p.title LIKE %:keyword% AND (:cursor IS NULL OR p.id < :cursor) ORDER BY p.id DESC")
    Slice<Post> findByTitleContaining(@Param("keyword") String keyword, @Param("cursor") Long cursor, Pageable pageable);

    Optional<Post> findByIdAndDeletedAtIsNull(Long id);
}