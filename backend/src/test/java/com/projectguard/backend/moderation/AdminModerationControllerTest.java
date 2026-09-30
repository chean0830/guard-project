package com.projectguard.backend.moderation;

import com.projectguard.backend.auth.User;
import com.projectguard.backend.auth.UserRepository;
import com.projectguard.backend.consultation.ConsultationMessageRepository;
import com.projectguard.backend.lawyer.LawyerRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminModerationController.class)
@TestPropertySource(properties = "ADMIN_SECRET=admin-secret")
class AdminModerationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ModerationService moderationService;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private LawyerRepository lawyerRepository;

    @MockitoBean
    private ConsultationMessageRepository messageRepository;

    @Test
    void 비밀키가_없으면_신고목록은_401을_반환한다() throws Exception {
        mockMvc.perform(get("/api/admin/reports"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(moderationService);
    }

    @Test
    void 비밀키가_틀리면_정지할_수_없다() throws Exception {
        mockMvc.perform(post("/api/admin/members/users/1/block").header("X-Admin-Secret", "wrong"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(moderationService);
    }

    @Test
    void 회원_목록에_정지여부와_신고횟수를_포함한다() throws Exception {
        User user = new User("blocked@example.com", "hash");
        user.block("욕설·모욕");
        when(userRepository.findAll()).thenReturn(List.of(user));

        mockMvc.perform(get("/api/admin/members/users").header("X-Admin-Secret", "admin-secret"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].email").value("blocked@example.com"))
                .andExpect(jsonPath("$[0].blocked").value(true))
                .andExpect(jsonPath("$[0].blockedReason").value("욕설·모욕"));
    }

    @Test
    void 사유와_함께_변호사를_정지한다() throws Exception {
        mockMvc.perform(post("/api/admin/members/lawyers/3/block")
                        .header("X-Admin-Secret", "admin-secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"금전 요구\"}"))
                .andExpect(status().isOk());

        verify(moderationService).blockLawyer(3L, "금전 요구");
    }

    @Test
    void 이미_처리된_신고를_다시_처리하면_409를_반환한다() throws Exception {
        doThrow(new IllegalStateException("이미 처리된 신고입니다.")).when(moderationService).actionReport(5L);

        mockMvc.perform(post("/api/admin/reports/5/action").header("X-Admin-Secret", "admin-secret"))
                .andExpect(status().isConflict());
    }
}
