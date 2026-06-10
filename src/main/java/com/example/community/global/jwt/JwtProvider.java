package com.example.community.global.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.time.Instant;
import java.util.Date;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class JwtProvider {

    private final JwtProperties jwtProperties;
    private Key key;
    // 초기화: Secret 키 길이 검증 (최소 256bit) 후 HMAC 키 생성
    @PostConstruct
    public void init() {
        byte[] keyBytes = jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {  // 256bit = 32byte
            throw new IllegalArgumentException("JWT secret must be at least 256 bits (32 characters)");
        }
        this.key = Keys.hmacShaKeyFor(keyBytes);
    }
    // JWT 토큰 생성 공통 메서드
    private String createToken(String type, Long userId, Map<String, Object> claims, long expSeconds) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("typ", type)
                .claims(claims)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(expSeconds)))
                .signWith((SecretKey) key, Jwts.SIG.HS256)
                .compact();
    }
    // 액세스 토큰 생성 (유효기간: JWT_ACCESS_EXP)
    public String createAccessToken(Long userId) {
        return createToken(
                "access",
                userId,
                Map.of(),  // 빈 map
                jwtProperties.getAccessTokenExpSeconds()
        );
    }
    // 리프레시 토큰 생성 (유효기간: JWT_REFRESH_EXP)
    public String createRefreshToken(Long userId) {
        return createToken(
                "refresh",
                userId,
                Map.of(),
                jwtProperties.getRefreshTokenExpSeconds()
        );
    }
    // JWT 토큰 파싱 및 서명 검증
    public Jws<Claims> parse(String token) {
        return Jwts.parser()
                .verifyWith((SecretKey) key)
                .build()
                .parseSignedClaims(token);
    }

    // 액세스 토큰 만료 시간 반환 (초)
    public long getAccessTokenExpSeconds() {
        return jwtProperties.getAccessTokenExpSeconds();
    }
    // 리프레시 토큰 만료 시간 반환 (초)
    public long getRefreshTokenExpSeconds() {
        return jwtProperties.getRefreshTokenExpSeconds();
    }
}
