package com.example.community.user.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;

@Getter

public class UpdateUserRequest {

    @NotBlank
    @Size(max = 10, message = "닉네임은 최대 10자까지 입력 가능합니다")
    @Pattern(
            regexp = "^\\S+$",
            message = "닉네임에 띄어쓰기를 사용할 수 없습니다"
    )
    private String nickname;

    @JsonProperty("profile_image")
    private String profileImage;
}
