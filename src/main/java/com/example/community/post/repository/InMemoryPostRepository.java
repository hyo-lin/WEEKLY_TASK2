package com.example.community.post.repository;

import com.example.community.post.model.Post;
import org.springframework.stereotype.Repository;

import java.util.*;

@Repository
public class InMemoryPostRepository implements PostRepository{

    // Map<Long, Post>를 메모리 저장소로 사용
    // key: postId, value: Post 객체
    // sequence로 postId 자동 증가
    private final Map<Long, Post> postMap=new HashMap<>();
    private Long sequence = 0L;

    @Override
    public Post save(Post post){
        if (post.getPostId() == null) {
            // 신규 생성: sequence 증가 후 id 부여
            Long newId = ++sequence;
            Post newPost = new Post(newId, post.getTitle(), post.getPostsContent(), post.getPostsImg(), post.getCreatedAt());
            postMap.put(newId, newPost);
            return newPost;
        } else {
            // 수정: 기존 id로 덮어쓰기
            postMap.put(post.getPostId(), post);
            return post;
        }
    }

    // 전체 조회: Map의 values를 List로 변환
    @Override
    public List<Post> findAll(){
        return new ArrayList<>(postMap.values());
    }

    // 단건 조회: Optional로 null 안전하게 처리
    @Override
    public Optional<Post> findById(Long id){
        return Optional.ofNullable(postMap.get(id));
    }

    // 삭제: Map에서 해당 id 제거
    @Override
    public void deleteById(Long id){
        postMap.remove(id);
    }
}
