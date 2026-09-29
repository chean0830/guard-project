package com.projectguard.backend.consultation;

import com.projectguard.backend.auth.AuthService;
import com.projectguard.backend.auth.User;
import com.projectguard.backend.lawyer.Lawyer;
import com.projectguard.backend.lawyer.LawyerRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ConsultationController.class)
class ConsultationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private ConsultationService consultationService;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private LawyerRepository lawyerRepository;

    private final User user = new User("user@example.com", "hash");

    @Test
    void 토큰이_없으면_401을_반환한다() throws Exception {
        mockMvc.perform(get("/api/consultations"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 문의를_시작하면_변호사_정보와_함께_반환한다() throws Exception {
        when(authService.validate("token-1")).thenReturn(Optional.of(user));
        Consultation consultation = new Consultation(1L, 2L);
        when(consultationService.startConsultation(any(), eq("보증금을 못 받았어요"))).thenReturn(consultation);
        Lawyer lawyer = new Lawyer("lawyer@example.com", "hash", "김변호", "법무법인 테스트", "12345");
        when(lawyerRepository.findById(2L)).thenReturn(Optional.of(lawyer));

        mockMvc.perform(post("/api/consultations")
                        .header("Authorization", "Bearer token-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ConsultationController.StartRequest("보증금을 못 받았어요"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lawyerName").value("김변호"));
    }

    @Test
    void 목록_조회시_요약_정보를_반환한다() throws Exception {
        when(authService.validate("token-2")).thenReturn(Optional.of(user));
        Consultation consultation = new Consultation(1L, 2L);
        when(consultationService.listForUser(any())).thenReturn(List.of(consultation));
        Lawyer lawyer = new Lawyer("lawyer@example.com", "hash", "김변호", "법무법인 테스트", "12345");
        when(lawyerRepository.findById(2L)).thenReturn(Optional.of(lawyer));

        mockMvc.perform(get("/api/consultations").header("Authorization", "Bearer token-2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].lawyerName").value("김변호"));
    }

    @Test
    void 다른_회원의_문의를_보려하면_403을_반환한다() throws Exception {
        when(authService.validate("token-3")).thenReturn(Optional.of(user));
        when(consultationService.getThreadAsUser(any(), any()))
                .thenThrow(new ConsultationAccessDeniedException("이 문의에 접근할 권한이 없습니다."));

        mockMvc.perform(get("/api/consultations/1").header("Authorization", "Bearer token-3"))
                .andExpect(status().isForbidden());
    }
}
