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
    private final S3Service s3Service;

    public void assignPost(Post post, String imageUrl) {
        postImageRepository.save(PostImage.create(post, imageUrl));
    }

    public void replaceImage(Post post, String imageUrl) {
        postImageRepository.findByPostIdAndDeletedAtIsNull(post.getId())
                .forEach(existing -> {
                    s3Service.deleteFile(existing.getImageUrl());  // S3 삭제 추가
                    existing.delete();
                });
        postImageRepository.save(PostImage.create(post, imageUrl));
    }

    public void softDeleteImages(Long postId) {
        postImageRepository.findByPostIdAndDeletedAtIsNull(postId)
                .forEach(image -> {
                    s3Service.deleteFile(image.getImageUrl());
                    image.delete();
                });
    }

    @Transactional(readOnly = true)
    public List<String> getImageUrls(Long postId) {
        return postImageRepository
                .findByPostIdAndDeletedAtIsNull(postId)
                .stream()
                .map(PostImage::getImageUrl)
                .toList();
    }
}