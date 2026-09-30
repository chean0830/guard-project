package com.projectguard.backend.admin;

import com.projectguard.backend.auth.InvalidCredentialsException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * 관리자 로그인. 관리자는 운영자 한 명뿐인 규모라 계정 테이블을 두지 않고, 서버 환경변수
 * (ADMIN_EMAIL/ADMIN_PASSWORD, .env에만 두고 저장소에는 올리지 않음)와 일치하면 관리자 세션
 * 토큰을 발급한다. 둘 중 하나라도 비어 있으면 관리자 로그인은 항상 실패한다.
 */
@Service
public class AdminAuthService {

    // 관리자 권한은 회원/변호사보다 훨씬 강하므로 세션을 짧게 둔다.
    private static final Duration TOKEN_TTL = Duration.ofHours(12);

    private final AdminAuthTokenRepository tokenRepository;
    private final String adminEmail;
    private final String adminPassword;

    public AdminAuthService(
            AdminAuthTokenRepository tokenRepository,
            @Value("${ADMIN_EMAIL:}") String adminEmail,
            @Value("${ADMIN_PASSWORD:}") String adminPassword
    ) {
        this.tokenRepository = tokenRepository;
        this.adminEmail = adminEmail;
        this.adminPassword = adminPassword;
    }

    public String login(String email, String password) {
        if (adminEmail.isBlank() || adminPassword.isBlank()
                || email == null || password == null
                || !adminEmail.equalsIgnoreCase(email.trim())
                || !constantTimeEquals(adminPassword, password)) {
            throw new InvalidCredentialsException("이메일 또는 비밀번호가 올바르지 않습니다.");
        }
        String token = UUID.randomUUID().toString();
        tokenRepository.save(new AdminAuthToken(token, Instant.now().plus(TOKEN_TTL)));
        return token;
    }

    public boolean isValid(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        return tokenRepository.findById(token)
                .filter(t -> t.getExpiresAt().isAfter(Instant.now()))
                .isPresent();
    }

    public void logout(String token) {
        if (token != null && !token.isBlank()) {
            tokenRepository.deleteById(token);
        }
    }

    public String getAdminEmail() {
        return adminEmail;
    }

    private boolean constantTimeEquals(String expected, String actual) {
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), actual.getBytes(StandardCharsets.UTF_8));
    }
}
