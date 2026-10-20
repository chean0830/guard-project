package com.projectguard.backend.api;

import com.projectguard.backend.auth.AuthService;
import com.projectguard.backend.auth.InvalidCredentialsException;
import com.projectguard.backend.auth.User;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AnalysisUploadTicketServiceTest {

    private final AuthService authService = mock(AuthService.class);
    private final AnalysisUploadTicketService service = new AnalysisUploadTicketService(authService);

    @Test
    void 입장권은_한_번만_쓸_수_있고_발급받은_세션을_돌려준다() {
        when(authService.validate("session-1")).thenReturn(Optional.of(mock(User.class)));

        String ticket = service.issue("session-1");

        assertThat(service.consume(ticket)).contains("session-1");
        assertThat(service.consume(ticket)).isEmpty();
    }

    @Test
    void 로그인하지_않았으면_입장권을_발급하지_않는다() {
        when(authService.validate(null)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.issue(null)).isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void 없는_입장권은_비어_있다() {
        assertThat(service.consume("unknown")).isEmpty();
        assertThat(service.consume(null)).isEmpty();
    }
}
