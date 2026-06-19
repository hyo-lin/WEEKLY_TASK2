package com.example.community.post.controller;

import com.example.community.global.response.CommonResponse;
import com.example.community.global.response.StatusCode;
import com.example.community.post.dto.request.PostCreateRequest;
import com.example.community.post.dto.request.PostUpdateRequest;
import com.example.community.post.dto.response.PostResponse;
import com.example.community.post.service.PostService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/posts")
public class PostController {

    private final PostService postService;

    // 게시물 검색
    @GetMapping("/search")
    public ResponseEntity<CommonResponse<List<PostResponse>>> searchPosts(
            @RequestAttribute("userId") Long userId,
            @RequestParam String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return ResponseEntity.ok(CommonResponse.success(
                StatusCode.GET_POSTS_SUCCESS,
                postService.searchPosts(userId, keyword, page, size)));
    }

    // 게시글 등록
    @PostMapping
    public ResponseEntity<CommonResponse<PostResponse>> createPost(
            @RequestAttribute("userId") Long userId,
            @RequestBody @Valid PostCreateRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(CommonResponse.success(StatusCode.CREATE_POST_SUCCESS, postService.createPost(userId, request)));
    }

    // 게시글 목록 조회(댓글 제외)
    @GetMapping
    public ResponseEntity<CommonResponse<List<PostResponse>>> getPosts(
            @RequestAttribute("userId") Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return ResponseEntity.ok(CommonResponse.success(StatusCode.GET_POSTS_SUCCESS, postService.getPosts(userId, page, size)));
    }

    // 게시물 상세조회
    @GetMapping("/{postId}")
    public ResponseEntity<CommonResponse<PostResponse>> getPost(
            @PathVariable Long postId,
            @RequestAttribute("userId") Long userId
    ) {
        return ResponseEntity.ok(CommonResponse.success(StatusCode.GET_POST_SUCCESS, postService.getPost(userId, postId)));
    }

    // 게시글 수정
    @PatchMapping("/{postId}")
    public ResponseEntity<CommonResponse<PostResponse>> updatePost(
            @PathVariable Long postId,
            @RequestAttribute("userId") Long userId,
            @RequestBody @Valid PostUpdateRequest request
    ){
        return ResponseEntity.ok(CommonResponse.success(StatusCode.UPDATE_POST_SUCCESS, postService.updatePost(postId, userId, request)));
    }

    // 게시글 제거
    @DeleteMapping("/{postId}")
    public ResponseEntity<CommonResponse<Void>> deletePost(
            @PathVariable Long postId,
            @RequestAttribute("userId") Long userId
    ) {
        postService.deletePost(postId,userId);
        return ResponseEntity.ok(CommonResponse.success(StatusCode.DELETE_POST_SUCCESS, null));
    }

}
