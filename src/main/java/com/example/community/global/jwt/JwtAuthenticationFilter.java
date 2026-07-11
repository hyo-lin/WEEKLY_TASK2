package com.example.community.global.jwt;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.util.PatternMatchUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtProvider jwtProvider;

    // 인증 없이 접근 가능한 경로
    private static final String[] WHITE_LIST = {
            "/users",
            "/users/email/check",
            "/users/nickname/check",
            "/auth/token",
            "/images/presigned-url/profile/temp",
            "/health/check"

    };


    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        String uri = request.getRequestURI();
        String method = request.getMethod();
        log.info("[JwtFilter] 요청 들어온 URI: {}, 메서드: {}", uri, method);

        if ("OPTIONS".equals(method)) {
            return true;
        }

        // 1. 정확히 회원가입(POST /users 또는 POST /users/) 요청인 경우 패스
        if (("POST".equals(method)) && ("/users".equals(uri) || "/users/".equals(uri))) {
            return true;
        }

        // 2. 나머지 화이트리스트 비교 (이메일 중복체크 등)
        if (PatternMatchUtils.simpleMatch(WHITE_LIST, uri)) {
            return true;
        }

        return false;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"message\":\"unauthorized\",\"data\":null}");
            return;
        }

        String token = authHeader.substring(7);

        try {
            io.jsonwebtoken.Claims payload = jwtProvider.parse(token).getPayload();

            if (!"access".equals(payload.get("typ", String.class))) {
                throw new IllegalArgumentException("Not access token");
            }

            Long userId = Long.valueOf(payload.getSubject());
            request.setAttribute("userId", userId);

            filterChain.doFilter(request, response);

        } catch (Exception e) {
            log.error("JWT 검증 실패: {}", e.getMessage());
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write(
                    "{\"message\":\"unauthorized\",\"data\":null}"
            );
        }
    }
}
