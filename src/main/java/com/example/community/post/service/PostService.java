package com.example.community.post.service;

import com.example.community.global.exception.GeneralException;
import com.example.community.global.response.StatusCode;
import com.example.community.image.service.PostImageService;
import com.example.community.post.dto.request.PostCreateRequest;
import com.example.community.post.dto.request.PostUpdateRequest;
import com.example.community.post.dto.response.PostResponse;
import com.example.community.post.model.Post;
import com.example.community.post.repository.PostRepository;
import com.example.community.postview.buffer.ViewCountBuffer;
import com.example.community.postview.event.PostViewedEvent;
import com.example.community.user.model.User;
import com.example.community.user.repository.UserRepository;
import org.springframework.context.ApplicationEventPublisher;
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
    private final PostImageService postImageService;
    private final ApplicationEventPublisher eventPublisher;
    private final ViewCountBuffer viewCountBuffer;


    @Transactional(readOnly = true)
    public List<PostResponse> searchPosts(String keyword) {
        return postRepository.searchByTitle(keyword).stream()
                .map(post -> PostResponse.from(post,
                        post.getViewCount() + viewCountBuffer.get(post.getId()),
                        List.of()))
                .collect(Collectors.toList());
    }

    // 게시글 추가
    // 제목 26자 초과 시 invalid_title_length 반환
    // 필수값 누락 시 missing_required_fields 반환
    @Transactional
    public PostResponse createPost(Long userId, PostCreateRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new GeneralException(StatusCode.USER_NOT_FOUND));
        Post post = postRepository.save(request.toEntity(user));

        if (request.getAttachFileUrl() != null) {
            postImageService.assignPost(post, request.getAttachFileUrl());
        }

        List<String> imageUrls = postImageService.getImageUrls(post.getId());
        return PostResponse.from(post, 0, imageUrls);
    }

    // 목록에서는 이미지 미포함
    @Transactional(readOnly = true)
    public List<PostResponse> getPosts(int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "createdAt"));
        List<Post> posts = postRepository.findAll(pageRequest).getContent();

        return posts.stream()
                .map(post -> PostResponse.from(post,
                        post.getViewCount() + viewCountBuffer.get(post.getId()),
                        List.of()))
                .collect(Collectors.toList());
    }

    // 게시글 상세조회, 이미지 URL 목록 포함
    @Transactional(readOnly = true)
    public PostResponse getPost(Long postId) {
        Post post = findPostOrThrow(postId);
        int viewCount = post.getViewCount() + viewCountBuffer.get(postId);

        eventPublisher.publishEvent(new PostViewedEvent(postId));

        List<String> imageUrls = postImageService.getImageUrls(postId);
        return PostResponse.from(post, viewCount, imageUrls);
    }

    // 게시글 수정
    @Transactional
    public PostResponse updatePost(Long postId, Long userId, PostUpdateRequest request) {
        Post post = findPostOrThrow(postId);
        if (!post.getUser().getId().equals(userId))
            throw new GeneralException(StatusCode.FORBIDDEN);

        post.update(request.getTitle(), request.getContent());
        // 새 이미지 URL이 있으면 기존 이미지 소프트딜리트 후 교체
        if (request.getAttachFileUrl() != null) {
            postImageService.replaceImage(post, request.getAttachFileUrl());
        }

        List<String> imageUrls = postImageService.getImageUrls(postId);
        return PostResponse.from(post, post.getViewCount(), imageUrls);
    }

    // 게시글 삭제
    @Transactional
    public void deletePost(Long postId, Long userId) {
        Post post = findPostOrThrow(postId);
        if (!post.getUser().getId().equals(userId)) {
            throw new GeneralException(StatusCode.FORBIDDEN);
        }
        postImageService.deleteIfExists(postId);
        postRepository.deleteById(postId);
    }

    // 공통: 게시글 조회 + 없으면 예외
    private Post findPostOrThrow(Long postId) {
        return postRepository.findById(postId)
                .orElseThrow(() -> new GeneralException(StatusCode.POST_NOT_FOUND));
    }
}
