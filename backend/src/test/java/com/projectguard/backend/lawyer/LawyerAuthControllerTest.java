package com.projectguard.backend.lawyer;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(LawyerAuthController.class)
class LawyerAuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private LawyerAuthService lawyerAuthService;

    @Test
    void 서류를_첨부해_가입하면_대기중_메시지를_반환한다() throws Exception {
        Lawyer pending = new Lawyer("lawyer@example.com", "hash", "김변호", "법무법인 테스트", "12345");
        MockMultipartFile document = new MockMultipartFile("documents", "license.pdf", "application/pdf", "dummy".getBytes());

        when(lawyerAuthService.signup(eq("lawyer@example.com"), eq("password123"), eq("김변호"),
                eq("법무법인 테스트"), eq("12345"), any()))
                .thenReturn(pending);

        mockMvc.perform(multipart("/api/lawyer/auth/signup")
                        .file(document)
                        .param("email", "lawyer@example.com")
                        .param("password", "password123")
                        .param("name", "김변호")
                        .param("lawFirm", "법무법인 테스트")
                        .param("barNumber", "12345"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.email").value("lawyer@example.com"));
    }

    @Test
    void 서류_없이_가입하면_400을_반환한다() throws Exception {
        when(lawyerAuthService.signup(any(), any(), any(), isNull(), any(), any()))
                .thenThrow(new IllegalArgumentException("변호사 자격을 확인할 수 있는 서류를 1개 이상 첨부해주세요."));

        mockMvc.perform(multipart("/api/lawyer/auth/signup")
                        .param("email", "lawyer2@example.com")
                        .param("password", "password123")
                        .param("name", "김변호")
                        .param("barNumber", "12345"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("변호사 자격을 확인할 수 있는 서류를 1개 이상 첨부해주세요."));
    }

    @Test
    void 승인_대기중이면_로그인시_403을_반환한다() throws Exception {
        when(lawyerAuthService.login(any(), any()))
                .thenThrow(new LawyerNotApprovedException("관리자 승인 대기 중입니다."));

        mockMvc.perform(post("/api/lawyer/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LawyerAuthController.LawyerLoginRequest("lawyer@example.com", "password123"))))
                .andExpect(status().isForbidden())
                .andExpect(content().string("관리자 승인 대기 중입니다."));
    }

    @Test
    void 승인된_변호사는_로그인에_성공한다() throws Exception {
        when(lawyerAuthService.login("approved@example.com", "password123"))
                .thenReturn(new LawyerAuthResult("token-abc", "approved@example.com", "김변호"));

        mockMvc.perform(post("/api/lawyer/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LawyerAuthController.LawyerLoginRequest("approved@example.com", "password123"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("token-abc"))
                .andExpect(jsonPath("$.name").value("김변호"));
    }

    @Test
    void 토큰이_유효하면_me가_변호사_정보를_반환한다() throws Exception {
        Lawyer lawyer = new Lawyer("me@example.com", "hash", "김변호", null, "12345");
        lawyer.approve();
        when(lawyerAuthService.validate("valid-token")).thenReturn(Optional.of(lawyer));

        mockMvc.perform(get("/api/lawyer/auth/me").header("Authorization", "Bearer valid-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("me@example.com"))
                .andExpect(jsonPath("$.status").value("APPROVED"));
    }

    @Test
    void 토큰이_없으면_me는_401을_반환한다() throws Exception {
        mockMvc.perform(get("/api/lawyer/auth/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 로그아웃하면_서비스에_토큰을_전달한다() throws Exception {
        mockMvc.perform(post("/api/lawyer/auth/logout").header("Authorization", "Bearer some-token"))
                .andExpect(status().isOk());

        verify(lawyerAuthService).logout("some-token");
    }
}
