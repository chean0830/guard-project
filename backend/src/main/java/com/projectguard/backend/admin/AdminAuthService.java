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

    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final Duration LOCK_DURATION = Duration.ofMinutes(15);

    private final AdminAuthTokenRepository tokenRepository;
    // 관리자는 계정 테이블이 없고 이메일 재설정도 없어서, 5회 실패하면 15분 동안 잠근다(서버 메모리).
    private int failedAttempts = 0;
    private Instant lockedUntil = Instant.EPOCH;
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

    public synchronized String login(String email, String password) {
        if (lockedUntil.isAfter(Instant.now())) {
            throw new InvalidCredentialsException("관리자 로그인을 " + MAX_FAILED_ATTEMPTS + "회 틀려 15분 동안 잠겼습니다. 잠시 후 다시 시도해주세요.");
        }
        if (adminEmail.isBlank() || adminPassword.isBlank()
                || email == null || password == null
                || !adminEmail.equalsIgnoreCase(email.trim())
                || !constantTimeEquals(adminPassword, password)) {
            if (++failedAttempts >= MAX_FAILED_ATTEMPTS) {
                failedAttempts = 0;
                lockedUntil = Instant.now().plus(LOCK_DURATION);
            }
            throw new InvalidCredentialsException("이메일 또는 비밀번호가 올바르지 않습니다.");
        }
        failedAttempts = 0;
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
