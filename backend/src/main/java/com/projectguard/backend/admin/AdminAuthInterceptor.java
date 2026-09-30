package com.projectguard.backend.admin;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * /api/admin/** 전체(로그인 경로 제외)를 한곳에서 보호한다. 두 가지 방식 중 하나면 통과:
 * - 관리자 로그인으로 받은 세션 토큰 (Authorization: Bearer ...) — 관리자 화면이 쓰는 방식
 * - ADMIN_SECRET 헤더 (X-Admin-Secret) — 브라우저 없이 서버/스크립트(e2e 준비 단계 등)가 호출할 때
 *
 * AdminAuthService를 ObjectProvider로 늦게 꺼내는 이유: @WebMvcTest 슬라이스는 모든 WebMvcConfigurer를
 * 불러오므로, 직접 주입하면 관리자와 무관한 컨트롤러 테스트까지 AdminAuthService 빈을 요구하게 된다.
 */
public class AdminAuthInterceptor implements HandlerInterceptor {

    private final ObjectProvider<AdminAuthService> adminAuthService;
    private final String adminSecret;

    public AdminAuthInterceptor(ObjectProvider<AdminAuthService> adminAuthService, String adminSecret) {
        this.adminAuthService = adminAuthService;
        this.adminSecret = adminSecret;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        AdminAuthService authService = adminAuthService.getIfAvailable();
        if ((authService != null && authService.isValid(extractBearer(request.getHeader("Authorization"))))
                || secretMatches(request.getHeader("X-Admin-Secret"))) {
            return true;
        }
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("text/plain;charset=UTF-8");
        response.getWriter().write("관리자 로그인이 필요합니다.");
        return false;
    }

    private boolean secretMatches(String provided) {
        return !adminSecret.isBlank() && provided != null
                && MessageDigest.isEqual(adminSecret.getBytes(StandardCharsets.UTF_8), provided.getBytes(StandardCharsets.UTF_8));
    }

    static String extractBearer(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return null;
        }
        return authorization.substring("Bearer ".length());
    }
}
