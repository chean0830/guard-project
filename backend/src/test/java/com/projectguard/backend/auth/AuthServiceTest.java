package com.projectguard.backend.auth;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 인메모리 H2 + 실제 JPA 리포지토리로 회원가입/로그인/토큰 검증/로그아웃 흐름 전체를 검증한다.
 * 외부 API를 부르지 않는 순수 DB 로직이라 @SpringBootTest로도 안전하다.
 */
@SpringBootTest
@Transactional
class AuthServiceTest {

    @Autowired
    private AuthService authService;

    @Test
    void 회원가입_후_발급된_토큰으로_검증에_성공한다() {
        AuthResult result = authService.signup("test1@example.com", "password123");

        assertNotEquals("", result.token());
        assertTrue(authService.validate(result.token()).isPresent());
        assertEquals("test1@example.com", authService.validate(result.token()).get().getEmail());
    }

    @Test
    void 같은_이메일로_또_가입하면_예외가_난다() {
        authService.signup("dup@example.com", "password123");
        assertThrows(EmailAlreadyExistsException.class, () -> authService.signup("dup@example.com", "password456"));
    }

    @Test
    void 이메일_형식이_아니면_예외가_난다() {
        assertThrows(IllegalArgumentException.class, () -> authService.signup("not-an-email", "password123"));
    }

    @Test
    void 비밀번호가_너무_짧으면_예외가_난다() {
        assertThrows(IllegalArgumentException.class, () -> authService.signup("short@example.com", "1234567"));
    }

    @Test
    void 올바른_비밀번호로_로그인하면_토큰을_받는다() {
        authService.signup("login@example.com", "password123");
        AuthResult result = authService.login("login@example.com", "password123");
        assertTrue(authService.validate(result.token()).isPresent());
    }

    @Test
    void 비밀번호가_틀리면_로그인에_실패한다() {
        authService.signup("wrongpw@example.com", "password123");
        assertThrows(InvalidCredentialsException.class, () -> authService.login("wrongpw@example.com", "wrongpassword"));
    }

    @Test
    void 가입하지_않은_이메일로_로그인하면_실패한다() {
        assertThrows(InvalidCredentialsException.class, () -> authService.login("nobody@example.com", "password123"));
    }

    @Test
    void 로그아웃하면_토큰이_더이상_유효하지_않다() {
        AuthResult result = authService.signup("logout@example.com", "password123");
        authService.logout(result.token());
        assertTrue(authService.validate(result.token()).isEmpty());
    }

    @Test
    void 존재하지_않는_토큰은_검증에_실패한다() {
        assertTrue(authService.validate("no-such-token").isEmpty());
    }

    @Test
    void 소셜_로그인은_처음이면_새_계정을_만든다() {
        AuthResult result = authService.oauthLogin("GOOGLE", "google-uid-1", "oauth1@example.com");

        assertTrue(authService.validate(result.token()).isPresent());
        assertEquals("GOOGLE", authService.validate(result.token()).get().getProvider());
    }

    @Test
    void 같은_이메일로_소셜_로그인하면_기존_계정으로_로그인된다() {
        AuthResult first = authService.oauthLogin("GOOGLE", "google-uid-2", "oauth2@example.com");
        AuthResult second = authService.oauthLogin("GOOGLE", "google-uid-2", "oauth2@example.com");

        assertEquals(authService.validate(first.token()).get().getId(), authService.validate(second.token()).get().getId());
    }

    @Test
    void 이메일로_가입한_계정과_같은_이메일로_소셜로그인하면_같은_계정으로_합쳐진다() {
        authService.signup("shared@example.com", "password123");
        AuthResult oauthResult = authService.oauthLogin("KAKAO", "kakao-uid-1", "shared@example.com");

        assertEquals("shared@example.com", authService.validate(oauthResult.token()).get().getEmail());
    }

    @Test
    void 소셜_로그인인데_이메일_동의를_안했으면_예외가_난다() {
        assertThrows(IllegalArgumentException.class, () -> authService.oauthLogin("KAKAO", "kakao-uid-2", null));
    }

    @Test
    void 프로필_이름을_수정할_수_있다() {
        AuthResult result = authService.signup("profile@example.com", "password123");
        Long userId = authService.validate(result.token()).get().getId();

        authService.updateProfile(userId, "홍길동");

        assertEquals("홍길동", authService.validate(result.token()).get().getName());
    }

    @Test
    void 현재_비밀번호가_맞으면_비밀번호를_변경할_수_있다() {
        AuthResult result = authService.signup("changepw@example.com", "password123");
        Long userId = authService.validate(result.token()).get().getId();

        authService.changePassword(userId, "password123", "newpassword456");

        assertTrue(authService.login("changepw@example.com", "newpassword456").token() != null);
        assertThrows(InvalidCredentialsException.class,
                () -> authService.login("changepw@example.com", "password123"));
    }

    @Test
    void 현재_비밀번호가_틀리면_비밀번호_변경에_실패한다() {
        AuthResult result = authService.signup("changepwfail@example.com", "password123");
        Long userId = authService.validate(result.token()).get().getId();

        assertThrows(InvalidCredentialsException.class,
                () -> authService.changePassword(userId, "wrongpassword", "newpassword456"));
    }

    @Test
    void 소셜_로그인_계정은_비밀번호를_변경할_수_없다() {
        AuthResult result = authService.oauthLogin("GOOGLE", "google-uid-3", "oauthpw@example.com");
        Long userId = authService.validate(result.token()).get().getId();

        assertThrows(IllegalArgumentException.class,
                () -> authService.changePassword(userId, "anything", "newpassword456"));
    }
}
