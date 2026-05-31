package com.example.community.auth.jwt;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.util.PatternMatchUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

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
            "/auth/check",
            "/images/**"
    };

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        String uri = request.getRequestURI();
        String method = request.getMethod();

        if ("OPTIONS".equals(method)) {
            return true;
        }

        //  화이트리스트에 매칭되는 주소들은 메서드(GET/POST) 상관없이 토큰 검증 제외하고 프리패스
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
            System.out.println("====== JWT 필터 검증 에러 발생!! ======");
            e.printStackTrace();

            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        }
    }
}
