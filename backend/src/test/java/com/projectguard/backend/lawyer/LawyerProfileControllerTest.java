package com.projectguard.backend.lawyer;

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

@WebMvcTest(LawyerProfileController.class)
class LawyerProfileControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private LawyerAuthService lawyerAuthService;

    private Lawyer approvedLawyer() {
        Lawyer lawyer = new Lawyer("lawyer@example.com", "hash", "김변호", "법무법인 테스트", "12345");
        lawyer.approve();
        return lawyer;
    }

    @Test
    void 토큰이_없으면_401을_반환한다() throws Exception {
        mockMvc.perform(get("/api/lawyer/profile"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 프로필을_조회할_수_있다() throws Exception {
        when(lawyerAuthService.validate("token-1")).thenReturn(Optional.of(approvedLawyer()));

        mockMvc.perform(get("/api/lawyer/profile").header("Authorization", "Bearer token-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("김변호"))
                .andExpect(jsonPath("$.status").value("APPROVED"));
    }

    @Test
    void 프로필을_수정할_수_있다() throws Exception {
        Lawyer lawyer = approvedLawyer();
        when(lawyerAuthService.validate("token-2")).thenReturn(Optional.of(lawyer));
        Lawyer updated = new Lawyer("lawyer@example.com", "hash", "새이름", "새소속", "12345");
        when(lawyerAuthService.updateProfile(eq(lawyer.getId()), eq("새이름"), eq("새소속"), any(), any()))
                .thenReturn(updated);

        mockMvc.perform(put("/api/lawyer/profile")
                        .header("Authorization", "Bearer token-2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LawyerProfileController.UpdateProfileRequest("새이름", "새소속", null, null, null, null, null, null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("새이름"));
    }

    @Test
    void 비밀번호_변경에_실패하면_400을_반환한다() throws Exception {
        Lawyer lawyer = approvedLawyer();
        when(lawyerAuthService.validate("token-3")).thenReturn(Optional.of(lawyer));
        org.mockito.Mockito.doThrow(new IllegalArgumentException("새 비밀번호는 8자 이상이어야 합니다."))
                .when(lawyerAuthService).changePassword(eq(lawyer.getId()), any(), any());

        mockMvc.perform(post("/api/lawyer/profile/password")
                        .header("Authorization", "Bearer token-3")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LawyerProfileController.ChangePasswordRequest("password123", "short"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 알림_설정을_변경하면_서비스에_전달된다() throws Exception {
        Lawyer lawyer = approvedLawyer();
        when(lawyerAuthService.validate("token-4")).thenReturn(Optional.of(lawyer));

        mockMvc.perform(put("/api/lawyer/profile/notifications")
                        .header("Authorization", "Bearer token-4")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LawyerProfileController.NotificationSettingRequest(false))))
                .andExpect(status().isOk());

        verify(lawyerAuthService).updateEmailNotificationsEnabled(lawyer.getId(), false);
    }
}
