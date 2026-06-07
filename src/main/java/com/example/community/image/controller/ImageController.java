package com.example.community.image.controller;

import com.example.community.global.response.CommonResponse;
import com.example.community.global.response.StatusCode;
import com.example.community.image.service.PostImageService;
import com.example.community.image.service.ProfileImageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/images")
@RequiredArgsConstructor
public class ImageController {

    private final PostImageService postImageService;
    private final ProfileImageService profileImageService;

    // 프로필 이미지 임시 업로드 (인증 불필요)
    // 회원가입/수정 전 이미지를 먼저 업로드하고 URL 반환
    // 반환된 URL은 회원가입/수정 시 profileImageUrl로 전달
    @PostMapping("/profile")
    public ResponseEntity<CommonResponse<Map<String, String>>> uploadProfileImage(
            @RequestParam("profileImage") MultipartFile file
    ) {
        String url = profileImageService.uploadTemp(file);
        return ResponseEntity.ok(CommonResponse.success(
                StatusCode.IMAGE_UPLOAD_SUCCESS,
                Map.of("profileImageUrl", url)
        ));
    }

    // 게시글 이미지 임시 업로드
    // 게시글 작성 전 이미지를 먼저 업로드하고 URL 반환
    // 반환된 URL은 게시글 작성 시 attach_file_url로 전달
    @PostMapping("/post")
    public ResponseEntity<CommonResponse<Map<String, String>>> uploadPostImage(
            @RequestAttribute("userId") Long userId,
            @RequestParam("postFile") MultipartFile file
    ) {
        String url = postImageService.uploadTemp(file);
        return ResponseEntity.ok(CommonResponse.success(
                StatusCode.IMAGE_UPLOAD_SUCCESS,
                Map.of("fileUrl", url)
        ));
    }


}