package com.example.community.image.repository;

import com.example.community.image.model.ProfileImage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ProfileImageRepository extends JpaRepository<ProfileImage, Long> {

    Optional<ProfileImage> findByUserIdAndDeletedAtIsNull(Long userId);
}
