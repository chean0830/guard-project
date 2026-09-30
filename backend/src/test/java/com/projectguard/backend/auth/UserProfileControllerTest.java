package com.projectguard.backend.auth;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserProfileController.class)
class UserProfileControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AccountDeletionService accountDeletionService;

    @MockitoBean
    private AuthService authService;

    @Test
    void 토큰이_없으면_401을_반환한다() throws Exception {
        mockMvc.perform(get("/api/profile"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 프로필을_조회할_수_있다() throws Exception {
        User user = new User("me@example.com", "hash");
        when(authService.validate("token-1")).thenReturn(Optional.of(user));

        mockMvc.perform(get("/api/profile").header("Authorization", "Bearer token-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("me@example.com"))
                .andExpect(jsonPath("$.provider").value("LOCAL"));
    }

    @Test
    void 이름을_수정하면_서비스에_전달된다() throws Exception {
        User user = new User("me@example.com", "hash");
        when(authService.validate("token-2")).thenReturn(Optional.of(user));

        mockMvc.perform(put("/api/profile")
                        .header("Authorization", "Bearer token-2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UserProfileController.UpdateProfileRequest("홍길동"))))
                .andExpect(status().isOk());

        verify(authService).updateProfile(user.getId(), "홍길동");
    }

    @Test
    void 소셜_계정이_비밀번호_변경을_시도하면_400을_반환한다() throws Exception {
        User user = new User("me@example.com", "GOOGLE", "uid-1");
        when(authService.validate("token-3")).thenReturn(Optional.of(user));
        org.mockito.Mockito.doThrow(new IllegalArgumentException("소셜 로그인 계정은 비밀번호를 변경할 수 없습니다."))
                .when(authService).changePassword(eq(user.getId()), any(), any());

        mockMvc.perform(post("/api/profile/password")
                        .header("Authorization", "Bearer token-3")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new UserProfileController.ChangePasswordRequest("x", "newpassword456"))))
                .andExpect(status().isBadRequest());
    }
}
