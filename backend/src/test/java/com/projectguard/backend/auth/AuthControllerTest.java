package com.projectguard.backend.auth;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@TestPropertySource(properties = "INTERNAL_SYNC_SECRET=test-secret")
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private EmailVerificationService emailVerificationService;

    @MockitoBean
    private AuthService authService;

    @Test
    void 회원가입_성공시_토큰을_반환한다() throws Exception {
        when(authService.signup("test@example.com", "password123"))
                .thenReturn(new AuthResult("token-abc", "test@example.com"));

        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new AuthController.AuthRequest("test@example.com", "password123"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("token-abc"))
                .andExpect(jsonPath("$.email").value("test@example.com"));
    }

    @Test
    void 이메일이_이미_있으면_409를_반환한다() throws Exception {
        when(authService.signup(any(), any())).thenThrow(new EmailAlreadyExistsException("이미 가입된 이메일입니다."));

        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new AuthController.AuthRequest("dup@example.com", "password123"))))
                .andExpect(status().isConflict())
                .andExpect(content().string("이미 가입된 이메일입니다."));
    }

    @Test
    void 로그인_실패시_401을_반환한다() throws Exception {
        when(authService.login(any(), any())).thenThrow(new InvalidCredentialsException("이메일 또는 비밀번호가 올바르지 않습니다."));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new AuthController.AuthRequest("test@example.com", "wrong"))))
                .andExpect(status().isUnauthorized())
                .andExpect(content().string("이메일 또는 비밀번호가 올바르지 않습니다."));
    }

    @Test
    void 토큰이_유효하면_me가_이메일을_반환한다() throws Exception {
        when(authService.validate("valid-token")).thenReturn(Optional.of(new User("me@example.com", "hash")));

        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer valid-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("me@example.com"));
    }

    @Test
    void 토큰이_없으면_me는_401을_반환한다() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 로그아웃하면_서비스에_토큰을_전달한다() throws Exception {
        mockMvc.perform(post("/api/auth/logout").header("Authorization", "Bearer some-token"))
                .andExpect(status().isOk());

        verify(authService).logout("some-token");
    }

    @Test
    void 내부_비밀키가_맞으면_소셜로그인을_동기화한다() throws Exception {
        when(authService.oauthLogin("GOOGLE", "uid-1", "oauth@example.com", true))
                .thenReturn(new AuthResult("token-xyz", "oauth@example.com"));

        mockMvc.perform(post("/api/auth/oauth-sync")
                        .header("X-Internal-Secret", "test-secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new AuthController.OAuthSyncRequest("GOOGLE", "uid-1", "oauth@example.com", true))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("token-xyz"));
    }

    @Test
    void 내부_비밀키가_틀리거나_없으면_401을_반환한다() throws Exception {
        mockMvc.perform(post("/api/auth/oauth-sync")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new AuthController.OAuthSyncRequest("GOOGLE", "uid-1", "oauth@example.com", true))))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/auth/oauth-sync")
                        .header("X-Internal-Secret", "wrong-secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new AuthController.OAuthSyncRequest("GOOGLE", "uid-1", "oauth@example.com", true))))
                .andExpect(status().isUnauthorized());
    }
}
