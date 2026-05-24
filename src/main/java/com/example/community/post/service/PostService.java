package com.example.community.post.service;

import com.example.community.global.exception.GeneralException;
import com.example.community.global.response.StatusCode;
import com.example.community.post.dto.request.PostCreateRequest;
import com.example.community.post.dto.request.PostUpdateRequest;
import com.example.community.post.dto.response.PostResponse;
import com.example.community.post.model.Post;
import com.example.community.post.repository.PostRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PostService {

    private final PostRepository postRepository;

    // 게시글 추가
    // 제목 26자 초과 시 invalid_title_length 반환
    // 필수값 누락 시 missing_required_fields 반환
    public PostResponse createPost(PostCreateRequest request){
        Post post=new Post(request.getTitle(), request.getPostsContent(), request.getPostsImg(), LocalDateTime.now().toString());
        Post savedPost=postRepository.save(post);
        return PostResponse.from(savedPost);
    }

    // 게시글 목록 조회
    public List<PostResponse> getPosts(){
        return postRepository.findAll().stream()
                .map(PostResponse::from)
                .collect(Collectors.toList());
    }

    // 게시글 수정
    // 존재하지 않는 postId면 post_not_found 반환
    public PostResponse updatePost(Long postId, PostUpdateRequest request){
        postRepository.findById(postId)
                .orElseThrow(()->new GeneralException(StatusCode.POST_NOT_FOUND));
        Post updatedPost = new Post(postId, request.getTitle(), request.getPostsContent(), request.getPostsImg(), LocalDateTime.now().toString());
        return PostResponse.from(postRepository.save(updatedPost));
    }

    // 게시글 삭제
    // 존재하지 않는 postId면 post_not_found 반환
    public void deletePost(Long postId){
        postRepository.findById(postId)
                .orElseThrow(()->new GeneralException(StatusCode.POST_NOT_FOUND));
        postRepository.deleteById(postId);
    }
}
