package com.example.community.post.dto.projection;

import java.time.LocalDateTime;

public interface PostSearchProjection {
    Long getPostId();
    Long getUserId();
    String getNickname();
    String getTitle();
    Integer getViewCount();
    Integer getLikeCount();
    Integer getCommentCount();
    LocalDateTime getCreatedAt();
}