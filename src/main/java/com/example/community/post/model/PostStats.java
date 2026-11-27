package com.example.community.post.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 게시글 카운터(좋아요·댓글 수). post 행과 분리해 카운터 갱신의 배타 락이 post 행에 걸리지 않게 한다.
 * 값은 엔티티 메서드가 아니라 PostStatsRepository 의 원자적 UPDATE 로만 바꾼다.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "post_stats")
public class PostStats {

    @Id
    @Column(name = "post_id")
    private Long postId;

    @Column(name = "like_count", nullable = false)
    private int likeCount = 0;

    @Column(name = "comment_count", nullable = false)
    private int commentCount = 0;
}
