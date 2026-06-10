package com.example.community.postview.repository;

import com.example.community.postview.model.PostView;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PostViewRepository extends JpaRepository<PostView, Long> {

    // 상세용: 단건 count
    long countByPostId(Long postId);

    // 목록용: 여러 게시글 count 한 번에
    @Query("SELECT pv.post.id, COUNT(pv) FROM PostView pv WHERE pv.post.id IN :postIds GROUP BY pv.post.id")
    List<Object[]> countByPostIds(@Param("postIds") List<Long> postIds);
}
