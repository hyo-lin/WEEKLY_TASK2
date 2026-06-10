package com.example.community.post.service;

import com.example.community.global.exception.GeneralException;
import com.example.community.global.response.StatusCode;
import com.example.community.image.service.PostImageService;
import com.example.community.post.dto.request.PostCreateRequest;
import com.example.community.post.dto.request.PostUpdateRequest;
import com.example.community.post.dto.response.PostResponse;
import com.example.community.post.model.Post;
import com.example.community.post.repository.PostRepository;
import com.example.community.postview.repository.PostViewRepository;
import com.example.community.postview.service.ViewCountService;
import com.example.community.user.model.User;
import com.example.community.user.repository.UserRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PostService {

    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final ViewCountService viewCountService;
    private final PostViewRepository postViewRepository;
    private final PostImageService postImageService;
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

    // 목록 조회:  DB 조회수와 Redis 캐시 조회수를 합산하여 반환
    // 목록에서는 이미지 미포함
    @Transactional(readOnly = true)
    public List<PostResponse> getPosts(int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "createdAt"));
        List<Post> posts = postRepository.findAll(pageRequest).getContent();

        List<Long> postIds = posts.stream()
                .map(Post::getId).collect(Collectors.toList());

        // DB 조회수: 쿼리 1번
        Map<Long, Integer> dbCountMap = postViewRepository
                .countByPostIds(postIds).stream()
                .collect(Collectors.toMap(
                        row -> (Long) row[0],
                        row -> ((Long) row[1]).intValue()));

        // Redis 조회수:  1번
        Map<Long, Integer> cachedCountMap =
                viewCountService.getCachedCounts(postIds);

        return posts.stream()
                .map(post -> PostResponse.from(post,
                        dbCountMap.getOrDefault(post.getId(), 0) +
                                cachedCountMap.getOrDefault(post.getId(), 0),
                        List.of()))
                .collect(Collectors.toList());
    }

    // 상세 조회: Redis +1 후 합산 조회수
    // 이미지 URL 목록 포함
    @Transactional(readOnly = true)
    public PostResponse getPost(Long postId) {
        Post post = findPostOrThrow(postId);
        viewCountService.increment(postId);
        int viewCount = viewCountService.getViewCount(postId);

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
        return PostResponse.from(post, viewCountService.getViewCount(postId), imageUrls);
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
