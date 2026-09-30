package com.projectguard.backend.auth;

import com.projectguard.backend.lawyer.Lawyer;
import com.projectguard.backend.lawyer.LawyerAuthService;
import com.projectguard.backend.lawyer.LawyerRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 계정 보안: 소셜 계정 연결 규칙(이메일 인증 여부), 비밀번호 5회 오류 잠금, 비밀번호 찾기로 잠금 해제.
 */
@SpringBootTest
@Transactional
class AccountSecurityTest {

    @Autowired
    private AuthService authService;

    @Autowired
    private LawyerAuthService lawyerAuthService;

    @Autowired
    private LawyerRepository lawyerRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordResetService passwordResetService;

    @Autowired
    private PasswordResetTokenRepository tokenRepository;

    private String email(String prefix) {
        return prefix + "-" + UUID.randomUUID() + "@example.com";
    }

    // ---- 1. 소셜 계정 연결

    @Test
    void 인증되지_않은_이메일로는_같은_이메일의_기존_계정에_들어갈_수_없다() {
        String victim = email("victim");
        authService.signup(victim, "password123");

        assertThrows(IllegalArgumentException.class, () -> authService.oauthLogin("KAKAO", "attacker-1", victim, false));
    }

    @Test
    void 인증된_이메일이면_같은_이메일의_기존_계정으로_로그인된다() {
        String owner = email("owner");
        Long userId = authService.validate(authService.signup(owner, "password123").token()).orElseThrow().getId();

        AuthResult result = authService.oauthLogin("GOOGLE", "google-1-" + UUID.randomUUID(), owner, true);

        assertEquals(userId, authService.validate(result.token()).orElseThrow().getId());
    }

    @Test
    void 이미_연결된_소셜_계정은_이메일_인증_여부와_상관없이_로그인된다() {
        String address = email("linked");
        String providerId = "kakao-" + UUID.randomUUID();
        AuthResult first = authService.oauthLogin("KAKAO", providerId, address, true);

        AuthResult again = authService.oauthLogin("KAKAO", providerId, address, false);

        assertEquals(authService.validate(first.token()).orElseThrow().getId(),
                authService.validate(again.token()).orElseThrow().getId());
    }

    // ---- 2. 5회 오류 잠금

    @Test
    void 비밀번호를_5회_틀리면_맞는_비밀번호로도_로그인할_수_없다() {
        String address = email("lock");
        authService.signup(address, "password123");

        for (int i = 0; i < 4; i++) {
            assertThrows(InvalidCredentialsException.class, () -> authService.login(address, "wrong-password"));
        }
        assertThrows(LoginLockedException.class, () -> authService.login(address, "wrong-password"));
        assertThrows(LoginLockedException.class, () -> authService.login(address, "password123"));
    }

    @Test
    void 성공하면_오류_횟수가_초기화된다() {
        String address = email("reset-count");
        authService.signup(address, "password123");
        for (int i = 0; i < 4; i++) {
            assertThrows(InvalidCredentialsException.class, () -> authService.login(address, "wrong-password"));
        }

        authService.login(address, "password123");

        assertEquals(0, userRepository.findByEmail(address).orElseThrow().getFailedLoginCount());
    }

    @Test
    void 변호사도_5회_틀리면_잠긴다() {
        String address = email("lawyer-lock");
        Lawyer lawyer = lawyerAuthService.signup(address, "password123", "김변호", null, "12345",
                List.of(new MockMultipartFile("documents", "license.pdf", "application/pdf", "dummy".getBytes())));
        lawyer.approve();
        lawyerRepository.save(lawyer);

        for (int i = 0; i < 4; i++) {
            assertThrows(InvalidCredentialsException.class, () -> lawyerAuthService.login(address, "wrong-password"));
        }
        assertThrows(LoginLockedException.class, () -> lawyerAuthService.login(address, "wrong-password"));
        assertThrows(LoginLockedException.class, () -> lawyerAuthService.login(address, "password123"));
    }

    // ---- 4. 비밀번호 찾기

    @Test
    void 비밀번호를_재설정하면_잠금이_풀리고_기존_세션은_끊긴다() {
        String address = email("forgot");
        String oldSession = authService.signup(address, "password123").token();
        for (int i = 0; i < 5; i++) {
            try {
                authService.login(address, "wrong-password");
            } catch (RuntimeException ignored) {
                // 잠금까지 틀린다
            }
        }
        assertTrue(userRepository.findByEmail(address).orElseThrow().isLoginLocked());

        String rawToken = issueTokenFor("USER", userRepository.findByEmail(address).orElseThrow().getId());
        passwordResetService.confirmReset(rawToken, "new-password-456");

        assertFalse(userRepository.findByEmail(address).orElseThrow().isLoginLocked());
        assertTrue(authService.validate(oldSession).isEmpty());
        assertTrue(authService.validate(authService.login(address, "new-password-456").token()).isPresent());
    }

    @Test
    void 재설정_링크는_한_번만_쓸_수_있다() {
        String address = email("once");
        authService.signup(address, "password123");
        String rawToken = issueTokenFor("USER", userRepository.findByEmail(address).orElseThrow().getId());

        passwordResetService.confirmReset(rawToken, "new-password-456");

        assertThrows(IllegalArgumentException.class, () -> passwordResetService.confirmReset(rawToken, "another-789"));
    }

    @Test
    void 없는_이메일로_요청해도_오류_없이_끝난다() {
        passwordResetService.requestReset(email("nobody"), "USER");
    }

    @Test
    void 재설정을_요청하면_토큰이_해시로만_저장된다() {
        String address = email("hashed");
        authService.signup(address, "password123");
        Long userId = userRepository.findByEmail(address).orElseThrow().getId();

        passwordResetService.requestReset(address, "USER");

        assertTrue(tokenRepository.findAll().stream()
                .anyMatch(t -> "USER".equals(t.getAccountType()) && userId.equals(t.getAccountId())));
    }

    /** 메일 대신 테스트에서 직접 토큰을 만든다 (원문은 메일로만 나가기 때문). */
    private String issueTokenFor(String type, Long accountId) {
        String raw = "test-token-" + UUID.randomUUID();
        tokenRepository.save(new PasswordResetToken(PasswordResetService.hash(raw), type, accountId, Instant.now().plusSeconds(600)));
        return raw;
    }
}
