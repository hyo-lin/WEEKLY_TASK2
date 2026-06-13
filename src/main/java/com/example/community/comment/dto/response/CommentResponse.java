package com.example.community.comment.dto.response;

import com.example.community.comment.model.Comment;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class CommentResponse {
    @JsonProperty("comment_id")
    private Long commentId;

    @JsonProperty("post_id")
    private Long postId;

    @JsonProperty("user_id")
    private Long userId;

    private String nickname;
    private String content;

    @JsonProperty("profile_image")
    private String profileImage;

    @JsonProperty("created_at")
    private LocalDateTime createdAt;

    public static CommentResponse from(Comment comment, String profileImageUrl) {
        return new CommentResponse(
                comment.getId(),
                comment.getPost().getId(),
                comment.getUser().getId(),
                comment.getUser().getNickname(),
                comment.getContent(),
                profileImageUrl,
                comment.getCreatedAt()
        );
    }
}
