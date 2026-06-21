package com.example.community.comment.repository;

import com.example.community.comment.model.Comment;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CommentRepository extends JpaRepository<Comment, Long> {

    @Query("SELECT c FROM Comment c WHERE c.post.id = :postId AND (:cursor IS NULL OR c.id > :cursor) ORDER BY c.id ASC")
    Slice<Comment> findAllByPostId(@Param("postId") Long postId, @Param("cursor") Long cursor, Pageable pageable);
}
