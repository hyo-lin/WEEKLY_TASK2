package com.example.community.post.repository;

import com.example.community.post.model.Post;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PostRepository {
    // 게시글 저장
    Post save(Post post);

    // 게시글 조회
    List<Post> findAll();

    // 게시글ID 찾아서 게시글 수정
    Optional<Post> findById(Long id);

    // 게시글ID로 찾아서 게시글 제거
    void deleteById(Long id);
}
