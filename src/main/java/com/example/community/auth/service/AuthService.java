package com.example.community.auth.service;

import com.example.community.auth.dto.request.LoginRequest;
import com.example.community.auth.dto.response.LoginResponse;
import com.example.community.auth.dto.response.LoginResult;
import com.example.community.auth.jwt.JwtProvider;
import com.example.community.global.exception.GeneralException;
import com.example.community.global.response.StatusCode;
import com.example.community.refreshtoken.model.RefreshToken;
import com.example.community.refreshtoken.repository.RefreshTokenRepository;
import com.example.community.user.model.User;
import com.example.community.user.repository.UserRepository;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtProvider jwtProvider;

    //로그인
    @Transactional
    public LoginResult login(LoginRequest request){
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new GeneralException(StatusCode.INVALID_CREDENTIALS));

        if (!user.getPassword().equals(request.getPassword())) {
            throw new GeneralException(StatusCode.INVALID_CREDENTIALS);
        }
        String accessToken = jwtProvider.createAccessToken(user.getId(), user.getEmail(), user.getNickname());
        String refreshToken = jwtProvider.createRefreshToken(user.getId());

        // 기존 리프레시 토큰 삭제 후 새로 저장 (1인 1토큰)
        refreshTokenRepository.deleteByUserId(user.getId());
        refreshTokenRepository.save(new RefreshToken(
                refreshToken,
                user.getId(),
                LocalDateTime.now().plusSeconds(jwtProvider.getAccessTokenExpSeconds() * 8)
        ));

        LoginResponse response = new LoginResponse(
                accessToken,
                jwtProvider.getAccessTokenExpSeconds(),
                user.getId(),
                user.getEmail(),
                user.getNickname()
        );

        return new LoginResult(response, refreshToken);

    }

    // 로그아웃
    @Transactional
    public void logout(Long userId){
    refreshTokenRepository.deleteByUserId(userId);
    }

    // 액세스 토큰 재발급
    @Transactional
    public LoginResponse refresh(String refreshToken) {
        RefreshToken saved = refreshTokenRepository.findByToken(refreshToken)
                .orElseThrow(() -> new GeneralException(StatusCode.INVALID_CREDENTIALS));

        if (saved.isExpired()) {
            refreshTokenRepository.delete(saved);
            throw new GeneralException(StatusCode.INVALID_CREDENTIALS);
        }

        User user = userRepository.findById(saved.getUserId())
                .orElseThrow(() -> new GeneralException(StatusCode.USER_NOT_FOUND));

        String newAccessToken = jwtProvider.createAccessToken(user.getId(), user.getEmail(), user.getNickname());

        return new LoginResponse(
                newAccessToken,
                jwtProvider.getAccessTokenExpSeconds(),
                user.getId(),
                user.getEmail(),
                user.getNickname()
        );
    }
}