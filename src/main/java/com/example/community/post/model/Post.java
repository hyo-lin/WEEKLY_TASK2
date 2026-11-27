package com.example.community.post.model;

import com.example.community.user.model.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.annotations.DynamicUpdate;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Slf4j
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@DynamicUpdate // 변경된 컬럼만 UPDATE: 제목 수정 등이 like/comment/view_count 를 오래된 값으로 덮어쓰지 않도록
@EntityListeners(AuditingEntityListener.class)
@Table(name = "post")
public class Post {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "post_id")
    private Long id;

    private String title;
    private String content;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @CreatedDate
    @Column(updatable = false)
    private LocalDateTime createdAt;

    // 카운터는 별도 테이블(post_stats)에 있다. post 와 PK 를 공유하는 읽기 전용 연관이며,
    // 목록/상세 조회는 LEFT JOIN FETCH 로 함께 읽는다(카운터 행이 없으면 null → 0). post → post_stats 방향의 FK 는 만들지 않는다(순환 방지).
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id", referencedColumnName = "post_id", insertable = false, updatable = false,
            foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private PostStats stats;

    @Column(nullable = false)
    private int viewCount = 0;

    private LocalDateTime deletedAt;

    public void delete() {
        this.deletedAt = LocalDateTime.now();
    }

    public void increaseViewCount(int count) {
        this.viewCount += count;
    }

    public int getLikeCount() {
        return stats == null ? 0 : stats.getLikeCount();
    }

    public int getCommentCount() {
        return stats == null ? 0 : stats.getCommentCount();
    }

    public static Post create(User user, String title, String content) {
        Post post = new Post();
        post.user = user;
        post.title = title;
        post.content = content;
        return post;
    }

    public void update(String title, String content) {
        this.title = title;
        this.content = content;
    }
}