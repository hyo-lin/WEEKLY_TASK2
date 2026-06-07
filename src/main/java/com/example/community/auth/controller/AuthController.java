package com.example.community.auth.controller;

import com.example.community.auth.dto.request.LoginRequest;
import com.example.community.auth.dto.response.LoginResponse;
import com.example.community.auth.dto.response.LoginResult;
import com.example.community.global.jwt.JwtProperties;
import com.example.community.auth.service.AuthService;
import com.example.community.global.response.CommonResponse;
import com.example.community.global.response.StatusCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;
    private final JwtProperties jwtProperties;

    // 로그인 상태 확인
    @GetMapping("/check")
    public ResponseEntity<CommonResponse<Map<String, Object>>> authCheck(
            @RequestAttribute(value = "userId", required = false) Long userId
    ) {

        if (userId == null) {
            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body(new CommonResponse<>(StatusCode.INVALID_CREDENTIALS.getMessage(), null));
        }
        Map<String, Object> dataMap = Map.of("profileImageUrl", "");

        return ResponseEntity.ok(CommonResponse.success(
                StatusCode.AUTH_CHECK_SUCCESS,
                dataMap
        ));
    }

    // 로그인
    @PostMapping("/token")
    public ResponseEntity<CommonResponse<LoginResponse>> login(
            @RequestBody @Valid LoginRequest request,
            HttpServletResponse response
    ){
        LoginResult result=authService.login(request);
        setRefreshTokenCookie(response, result.getRefreshToken());
        return ResponseEntity.ok(CommonResponse.success(StatusCode.LOGIN_SUCCESS, result.getResponse()));
    }

    // 로그아웃
    @DeleteMapping("/token")
    public ResponseEntity<CommonResponse<Void>> logout(
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        Long userId = (Long) request.getAttribute("userId");
        authService.logout(userId);
        deleteRefreshTokenCookie(response);
        return ResponseEntity.ok(CommonResponse.success(StatusCode.LOGOUT_SUCCESS, null));
    }

    // 액세스 토큰 재발급
    @PostMapping("/token/refresh")
    public ResponseEntity<CommonResponse<LoginResponse>> refresh(
            @CookieValue(name = "refreshToken", required = false) String refreshToken,
            HttpServletResponse response
    ) {
        LoginResult result = authService.refresh(refreshToken);
        setRefreshTokenCookie(response, result.getRefreshToken());
        return ResponseEntity.ok(CommonResponse.success(StatusCode.TOKEN_REFRESH_SUCCESS, result.getResponse()));
    }



    private void setRefreshTokenCookie(HttpServletResponse response, String refreshToken) {
        ResponseCookie cookie = ResponseCookie.from("refreshToken", refreshToken)
                .httpOnly(true)
                .secure(false)
                .path("/")
                .maxAge(jwtProperties.getRefreshTokenExpSeconds())
                .sameSite("Strict")
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }


    private void deleteRefreshTokenCookie(HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from("refreshToken", "")
                .httpOnly(true)
                .secure(false)
                .path("/")
                .maxAge(0)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

}
