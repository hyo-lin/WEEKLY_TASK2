package com.example.community.image.model;

import com.example.community.post.model.Post;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import java.time.LocalDateTime;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(name = "post_image")
public class PostImage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "post_id", nullable = true)
    private Post post;

    private String imageUrl;

    @CreatedDate
    @Column(updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime deletedAt;

    public static PostImage create(Post post, String imageUrl) {
        PostImage image = new PostImage();
        image.post = post;
        image.imageUrl = imageUrl;
        return image;
    }

    public void delete() {
        this.deletedAt = LocalDateTime.now();
    }

    public static PostImage createTemp(String imageUrl) {
        PostImage image = new PostImage();
        image.imageUrl = imageUrl;
        return image;
    }

    public void assignPost(Post post) {
        this.post = post;
    }
}
