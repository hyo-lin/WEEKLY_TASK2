package com.example.community.image.service;

import com.example.community.global.exception.GeneralException;
import com.example.community.global.response.StatusCode;
import com.example.community.image.model.ProfileImage;
import com.example.community.image.repository.ProfileImageRepository;
import com.example.community.user.model.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
@Transactional
public class ProfileImageService {

    private final ProfileImageRepository profileImageRepository;
    private final S3Service s3Service;

    public void saveImage(User user, String imageUrl) {
        profileImageRepository.save(ProfileImage.create(user, imageUrl));
    }

    public void delete(Long userId) {
        ProfileImage image = profileImageRepository
                .findByUserIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new GeneralException(StatusCode.IMAGE_NOT_FOUND));
        s3Service.deleteFile(image.getImageUrl());
        image.delete();
    }

    @Transactional(readOnly = true)
    public String getImageUrl(Long userId) {
        return profileImageRepository
                .findByUserIdAndDeletedAtIsNull(userId)
                .map(ProfileImage::getImageUrl)
                .orElse(null);
    }

    public void replaceImage(User user, String imageUrl) {
        profileImageRepository.findByUserIdAndDeletedAtIsNull(user.getId())
                .ifPresent(existing -> existing.delete());
        profileImageRepository.save(ProfileImage.create(user, imageUrl));
    }

    public void deleteIfExists(Long userId) {
        profileImageRepository.findByUserIdAndDeletedAtIsNull(userId)
                .ifPresent(image -> {
                    s3Service.deleteFile(image.getImageUrl());
                    image.delete();
                });
    }
}