package com.example.community.image.service;

import com.example.community.global.exception.GeneralException;
import com.example.community.global.response.StatusCode;
import com.example.community.image.model.PostImage;
import com.example.community.image.repository.PostImageRepository;
import com.example.community.post.model.Post;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class PostImageService {

    private final PostImageRepository postImageRepository;
    private final FileService fileService;

    // 게시글 이미지 임시 저장 (post_id 없이)
    // 게시글 작성 전 이미지를 먼저 저장하고 URL 반환
    public String uploadTemp(MultipartFile file) {
        String url = fileService.store(file);
        postImageRepository.save(PostImage.createTemp(url));
        return url;
    }

    // 임시 저장된 이미지에 게시글 연결
    // 게시글 생성 후 post_id를 연결하여 정식 이미지로 전환
    public void assignPost(Post post, String imageUrl) {
        postImageRepository.findByImageUrlAndPostIsNull(imageUrl)
                .ifPresent(image -> image.assignPost(post));
    }

    // 게시글 이미지 삭제
    // imageId와 postId 모두 검증하여 다른 게시글 이미지 삭제 방지
    public void delete(Long imageId, Long postId) {
        PostImage image = postImageRepository
                .findByIdAndPostIdAndDeletedAtIsNull(imageId, postId)
                .orElseThrow(() -> new GeneralException(StatusCode.IMAGE_NOT_FOUND));
        fileService.delete(image.getImageUrl());
        image.delete();
    }

    // 게시글 이미지 교체
    // 기존 이미지 소프트딜리트 후 새 이미지 연결
    public void replaceImage(Post post, String imageUrl) {
        postImageRepository.findByPostIdAndDeletedAtIsNull(post.getId())
                .forEach(existing -> existing.delete());
        postImageRepository.findByImageUrlAndPostIsNull(imageUrl)
                .ifPresent(image -> image.assignPost(post));
    }

    // 게시글 이미지 전체 삭제
    // 게시글 삭제 시 관련 이미지 모두 삭제
    public void deleteIfExists(Long postId) {
        postImageRepository.findByPostIdAndDeletedAtIsNull(postId)
                .forEach(image -> {
                    fileService.delete(image.getImageUrl());
                    image.delete();
                });
    }
    // 게시글 이미지 URL 목록 조회
    // 소프트딜리트 되지 않은 이미지만 반환
    @Transactional(readOnly = true)
    public List<String> getImageUrls(Long postId) {
        return postImageRepository
                .findByPostIdAndDeletedAtIsNull(postId)
                .stream()
                .map(PostImage::getImageUrl)
                .toList();
    }
}