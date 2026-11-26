package com.example.community.postlike.service;

import com.example.community.global.exception.GeneralException;
import com.example.community.global.response.StatusCode;
import com.example.community.post.repository.PostRepository;
import com.example.community.post.repository.PostStatsRepository;
import com.example.community.postlike.model.PostLike;
import com.example.community.postlike.repository.PostLikeRepository;
import com.example.community.user.model.User;
import com.example.community.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PostLikeService {

    private final PostLikeRepository postLikeRepository;
    private final PostRepository postRepository;
    private final PostStatsRepository postStatsRepository;
    private final UserRepository userRepository;

    // 좋아요
    // 카운터는 post 가 아니라 post_stats 행에 있다. INSERT 의 FK 검사는 post 행에 공유 락(S)만 잡고,
    // 카운터 UPDATE 의 배타 락(X)은 다른 행(post_stats)에 걸리므로 S→X 락 승격 데드락이 구조적으로 생기지 않는다.
    @Transactional
    public int like(Long postId, Long userId) {
        if (postLikeRepository.existsByPostIdAndUserId(postId, userId)) {
            throw new GeneralException(StatusCode.ALREADY_LIKED);
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new GeneralException(StatusCode.USER_NOT_FOUND));
        if (!postRepository.existsByIdAndDeletedAtIsNull(postId)) {
            throw new GeneralException(StatusCode.POST_NOT_FOUND);
        }
        try {
            postLikeRepository.saveAndFlush(PostLike.create(postRepository.getReferenceById(postId), user));
        } catch (DataIntegrityViolationException e) {
            // 같은 사용자의 동시 요청 중 먼저 커밋된 것이 있음 (unique 제약)
            throw new GeneralException(StatusCode.ALREADY_LIKED);
        }
        requireUpdated(postStatsRepository.increaseLikeCount(postId), postId);
        return postStatsRepository.findLikeCount(postId);
    }

    // 좋아요 취소: 실제로 행이 삭제된 요청만 카운터를 감소시킨다.
    @Transactional
    public int unlike(Long postId, Long userId) {
        if (postLikeRepository.deleteByPostIdAndUserId(postId, userId) == 0) {
            throw new GeneralException(StatusCode.LIKE_NOT_FOUND);
        }
        postStatsRepository.decreaseLikeCount(postId);
        return postStatsRepository.findLikeCount(postId);
    }

    private void requireUpdated(int updated, Long postId) {
        if (updated == 0) throw new IllegalStateException("post_stats 행이 없습니다. postId=" + postId);
    }
}
