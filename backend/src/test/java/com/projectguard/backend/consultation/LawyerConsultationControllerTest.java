package com.projectguard.backend.consultation;

import com.projectguard.backend.auth.User;
import com.projectguard.backend.auth.UserRepository;
import com.projectguard.backend.lawyer.Lawyer;
import com.projectguard.backend.lawyer.LawyerAuthService;
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

@WebMvcTest(LawyerConsultationController.class)
class LawyerConsultationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private ConsultationService consultationService;

    @MockitoBean
    private LawyerAuthService lawyerAuthService;

    @MockitoBean
    private UserRepository userRepository;

    private final Lawyer lawyer = new Lawyer("lawyer@example.com", "hash", "김변호", null, "12345");

    @Test
    void 토큰이_없으면_401을_반환한다() throws Exception {
        mockMvc.perform(get("/api/lawyer/consultations"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 목록_조회시_회원_표시명과_함께_반환한다() throws Exception {
        when(lawyerAuthService.validate("token-1")).thenReturn(Optional.of(lawyer));
        Consultation consultation = new Consultation(1L, 2L);
        when(consultationService.listForLawyer(any())).thenReturn(List.of(consultation));
        User user = new User("user@example.com", "hash");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        mockMvc.perform(get("/api/lawyer/consultations").header("Authorization", "Bearer token-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].userDisplayName").value("user@example.com"));
    }

    @Test
    void 답장을_보내면_메시지를_반환한다() throws Exception {
        when(lawyerAuthService.validate("token-2")).thenReturn(Optional.of(lawyer));
        ConsultationMessage message = new ConsultationMessage(1L, SenderType.LAWYER, "답변입니다");
        when(consultationService.postMessage(eq(1L), eq(SenderType.LAWYER), any(), eq("답변입니다")))
                .thenReturn(message);

        mockMvc.perform(post("/api/lawyer/consultations/1/messages")
                        .header("Authorization", "Bearer token-2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LawyerConsultationController.MessageRequest("답변입니다"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("답변입니다"));
    }

    @Test
    void 매칭되지_않은_문의에_접근하면_403을_반환한다() throws Exception {
        when(lawyerAuthService.validate("token-3")).thenReturn(Optional.of(lawyer));
        when(consultationService.getThreadAsLawyer(any(), any()))
                .thenThrow(new ConsultationAccessDeniedException("이 문의에 접근할 권한이 없습니다."));

        mockMvc.perform(get("/api/lawyer/consultations/1").header("Authorization", "Bearer token-3"))
                .andExpect(status().isForbidden());
    }
}
