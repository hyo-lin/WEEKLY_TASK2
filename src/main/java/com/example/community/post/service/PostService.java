package com.example.community.post.service;

import com.example.community.global.exception.GeneralException;
import com.example.community.global.response.StatusCode;
import com.example.community.post.dto.request.PostCreateRequest;
import com.example.community.post.dto.request.PostUpdateRequest;
import com.example.community.post.dto.response.PostResponse;
import com.example.community.post.model.Post;
import com.example.community.post.repository.PostRepository;
import com.example.community.user.model.User;
import com.example.community.user.repository.UserRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PostService {

    private final PostRepository postRepository;
    private final UserRepository userRepository;

    // 게시글 추가
    // 제목 26자 초과 시 invalid_title_length 반환
    // 필수값 누락 시 missing_required_fields 반환
    @Transactional
    public PostResponse createPost(Long userId, PostCreateRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new GeneralException(StatusCode.USER_NOT_FOUND));
        Post post = request.toEntity(user);
        return PostResponse.from(postRepository.save(post));
    }

    // 게시글 목록 조회
    @Transactional(readOnly = true)
    public List<PostResponse> getPosts(int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return postRepository.findAll(pageRequest).stream()
                .map(PostResponse::from)
                .collect(Collectors.toList());
    }
    // 게시물 상세 조회
    @Transactional(readOnly = true)
    public PostResponse getPost(Long postId) {
        return PostResponse.from(findPostOrThrow(postId));
    }
    // 게시글 수정
    @Transactional
    public PostResponse updatePost(Long postId, Long userId, PostUpdateRequest request) {
        Post post = findPostOrThrow(postId);
        if (!post.getUser().getId().equals(userId)) {
            throw new GeneralException(StatusCode.FORBIDDEN);
        }
        post.update(request.getTitle(), request.getContent(), request.getImageUrl());
        return PostResponse.from(post);
    }

    // 게시글 삭제
    @Transactional
    public void deletePost(Long postId, Long userId) {
        Post post = findPostOrThrow(postId);
        if (!post.getUser().getId().equals(userId)) {
            throw new GeneralException(StatusCode.FORBIDDEN);
        }
        postRepository.deleteById(postId);
    }

    // 공통: 게시글 조회 + 없으면 예외
    private Post findPostOrThrow(Long postId) {
        return postRepository.findById(postId)
                .orElseThrow(() -> new GeneralException(StatusCode.POST_NOT_FOUND));
    }
}
