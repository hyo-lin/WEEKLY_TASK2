package com.example.community.post.controller;

import com.example.community.global.response.CommonResponse;
import com.example.community.global.response.StatusCode;
import com.example.community.post.dto.request.PostCreateRequest;
import com.example.community.post.dto.request.PostUpdateRequest;
import com.example.community.post.dto.response.PostResponse;
import com.example.community.post.service.PostService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/posts")
public class PostController {

    private final PostService postService;

    // 게시글 등록
    @PostMapping
    public ResponseEntity<CommonResponse<PostResponse>> createPost(
            @RequestBody @Valid PostCreateRequest request
    ) {
        return ResponseEntity.ok(CommonResponse.success(StatusCode.CREATE_POST_SUCCESS, postService.createPost(request)));
    }

    // 게시글 조회(댓글 제외)
    @GetMapping
    public ResponseEntity<CommonResponse<List<PostResponse>>> getPosts() {
        return ResponseEntity.ok(CommonResponse.success(StatusCode.GET_POSTS_SUCCESS, postService.getPosts()));
    }

    // 게시글 수정
    @PatchMapping("/{postId}")
    public ResponseEntity<CommonResponse<PostResponse>> updatePost(
            @PathVariable Long postId,
            @RequestBody @Valid PostUpdateRequest request
    ){
        return ResponseEntity.ok(CommonResponse.success(StatusCode.UPDATE_POST_SUCCESS, postService.updatePost(postId, request)));
    }

    // 게시글 제거
    @DeleteMapping("/{postId}")
    public ResponseEntity<CommonResponse<Void>> deletePost(
            @PathVariable Long postId
    ) {
        postService.deletePost(postId);
        return ResponseEntity.ok(CommonResponse.success(StatusCode.DELETE_POST_SUCCESS, null));
    }



}
