package com.example.community.postlike.controller;

import com.example.community.global.response.CommonResponse;
import com.example.community.global.response.StatusCode;
import com.example.community.postlike.dto.LikeResponse;
import com.example.community.postlike.service.PostLikeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/posts/{postId}/likes")
public class PostLikeController {

    private final PostLikeService postLikeService;

    // 좋아요 등록
    @PostMapping
    public ResponseEntity<CommonResponse<LikeResponse>> like(
            @PathVariable Long postId,
            @RequestAttribute("userId") Long userId
    ) {
        int likeCount = postLikeService.like(postId, userId);
        return ResponseEntity.ok(CommonResponse.success(StatusCode.LIKE_SUCCESS,
                new LikeResponse(likeCount)));
    }

    // 좋아요 취소
    @DeleteMapping
    public ResponseEntity<CommonResponse<LikeResponse>> unlike(
            @PathVariable Long postId,
            @RequestAttribute("userId") Long userId
    ) {
        int likeCount = postLikeService.unlike(postId, userId);
        return ResponseEntity.ok(CommonResponse.success(StatusCode.UNLIKE_SUCCESS,
                new LikeResponse(likeCount)));
    }

}
