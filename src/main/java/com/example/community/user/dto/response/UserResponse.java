package com.example.community.user.dto.response;

import com.example.community.user.model.User;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class UserResponse {
    @JsonProperty("user_id")
    private Long userId;

    private String email;
    private String nickname;

    @JsonProperty("profile_image")
    private String profileImage;

    @JsonProperty("created_at")
    private LocalDateTime createdAt;

    @JsonProperty("is_active")
    private boolean active;

    public static UserResponse from(User user, String profileImageUrl) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getNickname(),
                profileImageUrl,
                user.getCreatedAt(),
                user.isActive()
        );
    }
}
