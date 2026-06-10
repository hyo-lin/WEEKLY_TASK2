package com.example.community.image.model;

import com.example.community.user.model.User;
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
@Table(name = "profile_image")
public class ProfileImage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = true)
    private User user;

    @Column(nullable = false)
    private String imageUrl;

    @CreatedDate
    @Column(updatable = false, nullable = false)
    private LocalDateTime createdAt;

    private LocalDateTime deletedAt;  // 소프트 딜리트용, nullable

    public static ProfileImage create(User user, String imageUrl) {
        ProfileImage image = new ProfileImage();
        image.user = user;
        image.imageUrl = imageUrl;
        return image;
    }

    public void delete() {
        this.deletedAt = LocalDateTime.now();
    }

    public static ProfileImage createTemp(String imageUrl) {
        ProfileImage image = new ProfileImage();
        image.imageUrl = imageUrl;
        return image;
    }

    public void assignUser(User user) {
        this.user = user;
    }
}
