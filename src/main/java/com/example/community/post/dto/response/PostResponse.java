package com.example.community.post.dto.response;

import com.example.community.post.model.Post;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class PostResponse {
    @JsonProperty("post_id")
    private Long postId;

    @JsonProperty("title")
    private String title;

    @JsonProperty("posts_content")
    private String postsContent;

    @JsonProperty("posts_img")
    private String postsImg;

    @JsonProperty("created_at")
    private String createdAt;

    public static PostResponse from(Post post) {
        return new PostResponse(post.getPostId(), post.getTitle(), post.getPostsContent(), post.getPostsImg(), post.getCreatedAt());
    }
}
