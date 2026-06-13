package com.example.community.post.dto.response;

import com.example.community.post.model.Post;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@AllArgsConstructor
public class PostResponse {

    @JsonProperty("post_id")
    private Long postId;

    private String title;

    @JsonProperty("post_content")
    private String content;

    @JsonProperty("user_id")
    private Long userId;

    private String nickname;

    @JsonProperty("like_count")
    private int likeCount;

    @JsonProperty("view_count")
    private int viewCount;

    @JsonProperty("comment_count")
    private int commentCount;

    @JsonProperty("profile_image")
    private String profileImage;

    @JsonProperty("image_urls")
    private List<String> imageUrls;

    @JsonProperty("created_at")
    private LocalDateTime createdAt;

    public static PostResponse from(Post post, int viewCount, List<String> imageUrls, String profileImageUrl) {
        return new PostResponse(
                post.getId(),
                post.getTitle(),
                post.getContent(),
                post.getUser().getId(),
                post.getUser().getNickname(),
                post.getLikeCount(),
                viewCount,
                post.getCommentCount(),
                profileImageUrl,
                imageUrls,
                post.getCreatedAt()
        );
    }
}
