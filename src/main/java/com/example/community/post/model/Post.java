package com.example.community.post.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class Post {
    private Long postId;
    private String title;
    private String postsContent;
    private String postsImg;
    private String createdAt;

    public Post(String title, String postsContent, String postsImg, String createdAt) {
        this.title = title;
        this.postsContent = postsContent;
        this.postsImg = postsImg;
        this.createdAt = createdAt;
    }
}
