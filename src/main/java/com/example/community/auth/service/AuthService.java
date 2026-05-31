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
    public LoginResult login(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new GeneralException(StatusCode.INVALID_CREDENTIALS));

        if (!user.getPassword().equals(request.getPassword())) {
            throw new GeneralException(StatusCode.INVALID_CREDENTIALS);
        }

        String accessToken = jwtProvider.createAccessToken(user.getId(), user.getEmail(), user.getNickname());
        String refreshToken = jwtProvider.createRefreshToken(user.getId());

        refreshTokenRepository.deleteByUserId(user.getId());
        refreshTokenRepository.save(new RefreshToken(
                refreshToken,
                user,
                LocalDateTime.now().plusSeconds(jwtProvider.getRefreshTokenExpSeconds())  // 수정
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
    public LoginResult refresh(String refreshToken) {  // LoginResult로 변경
        RefreshToken saved = refreshTokenRepository.findByToken(refreshToken)
                .orElseThrow(() -> new GeneralException(StatusCode.INVALID_CREDENTIALS));

        if (saved.isExpired()) {
            refreshTokenRepository.delete(saved);
            throw new GeneralException(StatusCode.INVALID_CREDENTIALS);
        }

        User user = userRepository.findById(saved.getUser().getId())
                .orElseThrow(() -> new GeneralException(StatusCode.USER_NOT_FOUND));

        // 기존 토큰 삭제
        refreshTokenRepository.delete(saved);

        // 새 토큰 발급 (RTR)
        String newAccessToken = jwtProvider.createAccessToken(user.getId(), user.getEmail(), user.getNickname());
        String newRefreshToken = jwtProvider.createRefreshToken(user.getId());

        refreshTokenRepository.save(new RefreshToken(
                newRefreshToken,
                user,
                LocalDateTime.now().plusSeconds(jwtProvider.getRefreshTokenExpSeconds())
        ));

        LoginResponse response = new LoginResponse(
                newAccessToken,
                jwtProvider.getAccessTokenExpSeconds(),
                user.getId(),
                user.getEmail(),
                user.getNickname()
        );

        return new LoginResult(response, newRefreshToken);
    }
}