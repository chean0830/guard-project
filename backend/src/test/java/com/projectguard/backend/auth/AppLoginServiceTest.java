package com.projectguard.backend.auth;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 앱 소셜 로그인 PKCE 교환: 맞는 verifier로 한 번만, 틀린 verifier로 시도하면 코드가 사라진다. */
@SpringBootTest
class AppLoginServiceTest {

    private static final String VERIFIER = "verifier-0123456789-abcdefghijklmnopqrstuvwxyz";

    @Autowired
    private AppLoginService appLoginService;

    @Test
    void 맞는_verifier면_세션_토큰을_돌려주고_코드는_한_번만_쓸_수_있다() {
        String code = appLoginService.issueCode("session-1", "me@example.com", AppLoginService.challengeOf(VERIFIER));

        AppLoginService.Exchanged result = appLoginService.exchange(code, VERIFIER);

        assertThat(result.token()).isEqualTo("session-1");
        assertThat(result.email()).isEqualTo("me@example.com");
        assertThatThrownBy(() -> appLoginService.exchange(code, VERIFIER)).isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void 틀린_verifier로_시도하면_실패하고_코드도_못_쓰게_된다() {
        String code = appLoginService.issueCode("session-2", "me@example.com", AppLoginService.challengeOf(VERIFIER));

        assertThatThrownBy(() -> appLoginService.exchange(code, VERIFIER.replace('a', 'b')))
                .isInstanceOf(InvalidCredentialsException.class);
        assertThatThrownBy(() -> appLoginService.exchange(code, VERIFIER)).isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void challenge_형식이_틀리면_코드를_발급하지_않는다() {
        assertThatThrownBy(() -> appLoginService.issueCode("session-3", "me@example.com", "short"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
