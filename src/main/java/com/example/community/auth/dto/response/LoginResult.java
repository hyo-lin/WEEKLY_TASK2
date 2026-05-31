package com.example.community.auth.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public class LoginResult {
    private LoginResponse response;
    private String refreshToken;
}
