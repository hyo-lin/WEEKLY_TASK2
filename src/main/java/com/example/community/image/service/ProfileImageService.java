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
    private final FileService fileService;

    // 프로필 이미지 임시 저장 (user_id 없이)
    // 회원가입/수정 전 이미지를 먼저 저장하고 URL 반환
    public String uploadTemp(MultipartFile file) {
        String url = fileService.store(file);
        profileImageRepository.save(ProfileImage.createTemp(url));
        return url;
    }

    // 임시 저장된 이미지에 유저 연결
    // 회원가입 후 user_id를 연결하여 정식 이미지로 전환
    public void assignUser(User user, String imageUrl) {
        profileImageRepository.findByImageUrlAndUserIsNull(imageUrl)
                .ifPresent(image -> image.assignUser(user));
    }

    public void delete(Long userId) {
        ProfileImage image = profileImageRepository
                .findByUserIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new GeneralException(StatusCode.IMAGE_NOT_FOUND));

        fileService.delete(image.getImageUrl());
        image.delete();
    }
    // 현재 활성 프로필 이미지 URL 조회
    // 이미지가 없으면 null 반환
    @Transactional(readOnly = true)
    public String getImageUrl(Long userId) {
        return profileImageRepository
                .findByUserIdAndDeletedAtIsNull(userId)
                .map(ProfileImage::getImageUrl)
                .orElse(null);
    }

    // 수정 시 기존 이미지 소프트딜리트 + 새 이미지 연결
    public void replaceImage(User user, String imageUrl) {
        profileImageRepository.findByUserIdAndDeletedAtIsNull(user.getId())
                .ifPresent(existing -> existing.delete());

        profileImageRepository.findByImageUrlAndUserIsNull(imageUrl)
                .ifPresent(image -> image.assignUser(user));
    }

    // 이미지가 있으면 삭제, 없으면 무시
    public void deleteIfExists(Long userId) {
        profileImageRepository.findByUserIdAndDeletedAtIsNull(userId)
                .ifPresent(image -> {
                    fileService.delete(image.getImageUrl());
                    image.delete();
                });
    }
}