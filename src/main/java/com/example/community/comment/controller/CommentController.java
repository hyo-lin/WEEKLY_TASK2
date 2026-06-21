package com.example.community.comment.controller;

import com.example.community.comment.dto.request.CommentCreateRequest;
import com.example.community.comment.dto.request.CommentUpdateRequest;
import com.example.community.comment.dto.response.CommentCursorResponse;
import com.example.community.comment.dto.response.CommentResponse;
import com.example.community.comment.service.CommentService;
import com.example.community.global.response.CommonResponse;
import com.example.community.global.response.StatusCode;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;


@RequiredArgsConstructor
@RestController
@RequestMapping("/posts/{postId}/comments")
public class CommentController {

    private final CommentService commentService;

    // 댓글 등록
    @PostMapping
    public ResponseEntity<CommonResponse<CommentResponse>> createComment(
            @PathVariable Long postId,
            @RequestAttribute("userId") Long userId,
            @RequestBody @Valid CommentCreateRequest request
    ){
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(CommonResponse.success(StatusCode.CREATE_COMMENT_SUCCESS, commentService.createComment(postId, userId, request)));
    }

    // 댓글 목록 조회
    @GetMapping
    public ResponseEntity<CommonResponse<CommentCursorResponse>> getComments(
            @PathVariable Long postId,
            @RequestAttribute("userId") Long userId,
            @RequestParam(required = false) Long cursor,
            @RequestParam(defaultValue = "10") int size
    ) {
        return ResponseEntity.ok(CommonResponse.success(StatusCode.GET_COMMENTS_SUCCESS, commentService.getComments(postId, cursor, size)));
    }

    //댓글 수정
    @PatchMapping("/{commentId}")
    public ResponseEntity<CommonResponse<CommentResponse>> updateComment(
            @PathVariable Long postId,
            @PathVariable Long commentId,
            @RequestAttribute("userId") Long userId,
            @RequestBody @Valid CommentUpdateRequest request
    ){
        return ResponseEntity.ok(CommonResponse.success(StatusCode.UPDATE_COMMENT_SUCCESS, commentService.updateComment(commentId, userId, request)));
    }

    //댓글 삭제
    @DeleteMapping("/{commentId}")
    public ResponseEntity<CommonResponse<Void>> deleteComment(
            @PathVariable Long postId,
            @PathVariable Long commentId,
            @RequestAttribute("userId") Long userId
    ){
        commentService.deleteComment(commentId,userId);
        return ResponseEntity.ok(CommonResponse.success(StatusCode.DELETE_COMMENT_SUCCESS, null));
    }

}
