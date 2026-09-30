package com.projectguard.backend.auth;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 회원가입 이메일 인증: 인증번호 확인, 5회 오답 무효, 1회용 가입 토큰, 하루 발송 제한. */
@SpringBootTest
@Transactional
class EmailVerificationServiceTest {

    @Autowired
    private EmailVerificationService verificationService;

    @Autowired
    private EmailVerificationRepository repository;

    @Autowired
    private AuthService authService;

    private String email() {
        return "verify-" + UUID.randomUUID() + "@example.com";
    }

    /** 메일로 나가는 인증번호 원문은 알 수 없으니, 테스트에서는 알려진 번호로 인증 기록을 직접 만든다. */
    private void issueKnownCode(String email, String code) {
        repository.deleteByEmail(email);
        repository.save(new EmailVerification(email, PasswordResetService.hash(email + ":" + code), Instant.now().plusSeconds(600)));
    }

    @Test
    void 인증번호가_맞으면_가입_토큰을_받고_한_번만_쓸_수_있다() {
        String email = email();
        issueKnownCode(email, "123456");

        String token = verificationService.confirmCode(email, "123456");
        verificationService.consumeToken(email, token);

        assertThrows(IllegalArgumentException.class, () -> verificationService.consumeToken(email, token));
    }

    @Test
    void 다른_이메일의_토큰으로는_가입할_수_없다() {
        String email = email();
        issueKnownCode(email, "123456");
        String token = verificationService.confirmCode(email, "123456");

        assertThrows(IllegalArgumentException.class, () -> verificationService.consumeToken(email(), token));
    }

    @Test
    void 인증번호를_5번_틀리면_맞는_번호도_거부된다() {
        String email = email();
        issueKnownCode(email, "123456");
        for (int i = 0; i < 5; i++) {
            assertThrows(IllegalArgumentException.class, () -> verificationService.confirmCode(email, "000000"));
        }
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> verificationService.confirmCode(email, "123456"));
        assertTrue(e.getMessage().contains("다시 받아주세요"));
    }

    @Test
    void 이미_가입된_이메일로는_인증번호를_받을_수_없다() {
        String email = email();
        authService.signup(email, "password123");

        assertThrows(EmailAlreadyExistsException.class, () -> verificationService.requestCode(email));
    }

    @Test
    void 인증번호는_같은_이메일로_하루_5번까지만_보낸다() {
        String email = email();
        for (int i = 0; i < 5; i++) {
            verificationService.requestCode(email);
        }
        assertThrows(IllegalStateException.class, () -> verificationService.requestCode(email));
    }
}
