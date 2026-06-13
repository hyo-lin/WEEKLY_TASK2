package com.example.community.comment.service;

import com.example.community.comment.dto.request.CommentCreateRequest;
import com.example.community.comment.dto.request.CommentUpdateRequest;
import com.example.community.comment.dto.response.CommentResponse;
import com.example.community.comment.model.Comment;
import com.example.community.comment.repository.CommentRepository;
import com.example.community.global.exception.GeneralException;
import com.example.community.global.response.StatusCode;
import com.example.community.image.service.ProfileImageService;
import com.example.community.post.model.Post;
import com.example.community.post.repository.PostRepository;
import com.example.community.user.model.User;
import com.example.community.user.repository.UserRepository;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CommentService {

    private final CommentRepository commentRepository;
    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final ProfileImageService profileImageService;


    @Transactional
    public CommentResponse createComment(Long postId, Long userId, CommentCreateRequest request) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new GeneralException(StatusCode.POST_NOT_FOUND));
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new GeneralException(StatusCode.USER_NOT_FOUND));
        Comment comment = Comment.create(post, user, request.getContent());
        post.increaseCommentCount();
        return CommentResponse.from(
                commentRepository.save(comment),
                profileImageService.getImageUrl(userId)
        );
    }
    // 댓글 목록 조회
    @Transactional(readOnly = true)
    public List<CommentResponse> getComments(Long postId) {
        return commentRepository.findAllByPostId(postId).stream()
                .map(comment -> CommentResponse.from(
                        comment,
                        profileImageService.getImageUrl(comment.getUser().getId())
                ))
                .collect(Collectors.toList());
    }

    // 댓글 수정
    @Transactional
    public CommentResponse updateComment(Long commentId, Long userId, CommentUpdateRequest request) {
        Comment comment = findCommentOrThrow(commentId);
        if (!comment.getUser().getId().equals(userId)) {
            throw new GeneralException(StatusCode.FORBIDDEN);
        }
        comment.update(request.getContent());
        return CommentResponse.from(
                comment,
                profileImageService.getImageUrl(userId)
        );
    }

    // 댓글 삭제
    @Transactional
    public void deleteComment(Long commentId, Long userId) {
        Comment comment = findCommentOrThrow(commentId);
        if (!comment.getUser().getId().equals(userId)) {
            throw new GeneralException(StatusCode.FORBIDDEN);
        }
        comment.getPost().decreaseCommentCount();
        commentRepository.deleteById(commentId);
    }

    private Comment findCommentOrThrow(Long commentId) {
        return commentRepository.findById(commentId)
                .orElseThrow(() -> new GeneralException(StatusCode.COMMENT_NOT_FOUND));
    }

}
