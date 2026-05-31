package com.example.community.post.dto.response;

import com.example.community.post.model.Post;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class PostResponse {

    @JsonProperty("post_id")
    private Long postId;

    private String title;

    @JsonProperty("post_content")
    private String content;

    @JsonProperty("post_image_url")
    private String imageUrl;

    @JsonProperty("user_id")
    private Long userId;

    private String nickname;

    @JsonProperty("like_count")
    private int likeCount;

    @JsonProperty("view_count")
    private int viewCount;

    @JsonProperty("comment_count")
    private int commentCount;

    @JsonProperty("created_at")
    private LocalDateTime createdAt;

    public static PostResponse from(Post post) {
        return new PostResponse(
                post.getId(),
                post.getTitle(),
                post.getContent(),
                post.getImageUrl(),
                post.getUser().getId(),
                post.getUser().getNickname(),
                post.getLikeCount(),
                post.getViewCount(),
                post.getCommentCount(),
                post.getCreatedAt()
        );
    }
}
