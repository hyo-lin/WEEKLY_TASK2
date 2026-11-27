package com.example.community.comment.service;

import com.example.community.comment.dto.request.CommentCreateRequest;
import com.example.community.comment.dto.request.CommentUpdateRequest;
import com.example.community.comment.dto.response.CommentCursorResponse;
import com.example.community.comment.dto.response.CommentResponse;
import com.example.community.comment.model.Comment;
import com.example.community.comment.repository.CommentRepository;
import com.example.community.global.exception.GeneralException;
import com.example.community.global.response.StatusCode;
import com.example.community.image.service.ProfileImageService;
import com.example.community.post.repository.PostRepository;
import com.example.community.post.repository.PostStatsRepository;
import com.example.community.user.model.User;
import com.example.community.user.repository.UserRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
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
    private final PostStatsRepository postStatsRepository;
    private final UserRepository userRepository;
    private final ProfileImageService profileImageService;


    // 카운터는 post_stats 에 있어 INSERT(post 공유 락)와 카운터 UPDATE(post_stats 배타 락)가 서로 다른 행을 잠근다. (PostLikeService.like 와 동일)
    @Transactional
    public CommentResponse createComment(Long postId, Long userId, CommentCreateRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new GeneralException(StatusCode.USER_NOT_FOUND));
        if (!postRepository.existsByIdAndDeletedAtIsNull(postId)) {
            throw new GeneralException(StatusCode.POST_NOT_FOUND);
        }
        Comment comment = commentRepository.save(
                Comment.create(postRepository.getReferenceById(postId), user, request.getContent()));
        if (postStatsRepository.increaseCommentCount(postId) == 0) {
            throw new IllegalStateException("post_stats 행이 없습니다. postId=" + postId);
        }
        return CommentResponse.from(comment, profileImageService.getImageUrl(userId));
    }

    // 댓글 목록 조회
    @Transactional(readOnly = true)
    public CommentCursorResponse getComments(Long postId, Long cursor, int size) {
        Slice<Comment> slice = commentRepository.findAllByPostId(postId, cursor, PageRequest.of(0, size));
        List<CommentResponse> comments = slice.getContent().stream()
                .map(comment -> CommentResponse.from(
                        comment,
                        profileImageService.getImageUrl(comment.getUser().getId())
                ))
                .collect(Collectors.toList());
        return new CommentCursorResponse(comments, slice.hasNext());
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
        Long postId = comment.getPost().getId(); // 프록시 id 접근이라 추가 쿼리 없음
        // 실제로 행이 삭제된 요청만 카운터를 감소시킨다 (같은 댓글 동시 삭제 대비)
        if (commentRepository.deleteByIdReturningCount(commentId) == 0) {
            throw new GeneralException(StatusCode.COMMENT_NOT_FOUND);
        }
        postStatsRepository.decreaseCommentCount(postId);
    }

    private Comment findCommentOrThrow(Long commentId) {
        return commentRepository.findById(commentId)
                .orElseThrow(() -> new GeneralException(StatusCode.COMMENT_NOT_FOUND));
    }

}
