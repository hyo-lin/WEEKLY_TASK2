package com.example.community.image.repository;

import com.example.community.image.model.PostImage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PostImageRepository extends JpaRepository<PostImage, Long> {
    List<PostImage> findByPostIdAndDeletedAtIsNull(Long postId);
    Optional<PostImage> findByIdAndPostIdAndDeletedAtIsNull(Long id, Long postId);

    Optional<PostImage> findByImageUrlAndPostIsNull(String imageUrl);
}
