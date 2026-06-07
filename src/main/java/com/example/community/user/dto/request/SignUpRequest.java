package com.example.community.user.dto.request;

import com.example.community.user.model.User;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;

@Getter
public class SignUpRequest {

    @NotBlank
    @Email
    private String email;

    @NotBlank
    @Size(min = 8, max = 20)
    @Pattern(
            regexp = "^(?=.*[A-Z])(?=.*[a-z])(?=.*\\d)(?=.*[@$!%*#?&])[A-Za-z\\d@$!%*#?&]{8,}$",
            message = "비밀번호는 대문자, 소문자, 숫자, 특수문자를 최소 1개씩 포함해야 합니다"
    )
    private String password;

    @NotBlank
    @JsonProperty("password_confirm")
    private String passwordConfirm;

    @NotBlank
    @Size(max = 10, message = "닉네임은 최대 10자까지 입력 가능합니다")
    @Pattern(
            regexp = "^\\S+$",
            message = "닉네임에 띄어쓰기를 사용할 수 없습니다"
    )
    private String nickname;

    @JsonProperty("profileImageUrl")
    private String profileImageUrl;

    public User toEntity() {
        return User.create(email, password, nickname);
    }
}
