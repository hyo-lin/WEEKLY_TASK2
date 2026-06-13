package com.example.community.post.repository;

import com.example.community.post.model.Post;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface PostRepository extends JpaRepository<Post, Long> {

    @Query("SELECT p FROM Post p WHERE p.deletedAt IS NULL AND p.title LIKE %:keyword%")
    Page<Post> findByTitleContaining(String keyword, Pageable pageable);

    @Query("SELECT p FROM Post p WHERE p.deletedAt IS NULL")
    Page<Post> findAllActive(Pageable pageable);

    Optional<Post> findByIdAndDeletedAtIsNull(Long id);
}