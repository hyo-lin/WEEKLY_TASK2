package com.example.community.global.file.controller;

import com.example.community.global.file.service.ImageService;
import com.example.community.global.response.CommonResponse;
import com.example.community.global.response.StatusCode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/images")
public class ImageController {

    private final ImageService imageService;

    // 프로필 이미지 업로드
    @PostMapping("/profile")
    public ResponseEntity<CommonResponse<Map<String, String>>> uploadProfileImage(
            @RequestParam("profileImage") MultipartFile file
    ) {
        String url = imageService.upload(file);
        return ResponseEntity.ok(CommonResponse.success(
                StatusCode.UPLOAD_IMAGE_SUCCESS,
                Map.of("profileImageUrl", url)
        ));
    }

    // 게시글 이미지 업로드
    @PostMapping("/post")
    public ResponseEntity<CommonResponse<Map<String, String>>> uploadPostImage(
            @RequestParam("file") MultipartFile file
    ) {
        String url = imageService.upload(file);
        return ResponseEntity.ok(CommonResponse.success(
                StatusCode.UPLOAD_IMAGE_SUCCESS,
                Map.of("fileUrl", url)
        ));
    }
}
