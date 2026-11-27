package com.example.community.postlike.repository;

import com.example.community.postlike.model.PostLike;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PostLikeRepository extends JpaRepository<PostLike, Long> {
    Optional<PostLike> findByPostIdAndUserId(Long postId, Long userId);
    boolean existsByPostIdAndUserId(Long postId, Long userId);
    void deleteByPostId(Long postId);

    // 실제로 삭제된 행 수를 돌려준다 (동시 취소 요청 중 1건만 1을 받음)
    @Modifying
    @Query("DELETE FROM PostLike l WHERE l.post.id = :postId AND l.user.id = :userId")
    int deleteByPostIdAndUserId(@Param("postId") Long postId, @Param("userId") Long userId);

}
