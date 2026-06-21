package com.example.community.postlike.service;

import com.example.community.global.exception.GeneralException;
import com.example.community.global.response.StatusCode;
import com.example.community.post.model.Post;
import com.example.community.post.repository.PostRepository;
import com.example.community.postlike.model.PostLike;
import com.example.community.postlike.repository.PostLikeRepository;
import com.example.community.user.model.User;
import com.example.community.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PostLikeService {

    private final PostLikeRepository postLikeRepository;
    private final PostRepository postRepository;
    private final UserRepository userRepository;

    // 좋아요
    @Transactional
    public int like(Long postId, Long userId) {
        if (postLikeRepository.existsByPostIdAndUserId(postId, userId)) {
            throw new GeneralException(StatusCode.ALREADY_LIKED);
        }
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new GeneralException(StatusCode.POST_NOT_FOUND));
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new GeneralException(StatusCode.USER_NOT_FOUND));

        postLikeRepository.save(PostLike.create(post, user));
        post.increaseLikeCount();
        return post.getLikeCount();
    }

    // 좋아요 취소
    @Transactional
    public int unlike(Long postId, Long userId) {
        PostLike postLike = postLikeRepository.findByPostIdAndUserId(postId, userId)
                .orElseThrow(() -> new GeneralException(StatusCode.LIKE_NOT_FOUND));

        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new GeneralException(StatusCode.POST_NOT_FOUND));

        postLikeRepository.delete(postLike);
        post.decreaseLikeCount();  // like_count 감소
        return post.getLikeCount();
    }

    }
