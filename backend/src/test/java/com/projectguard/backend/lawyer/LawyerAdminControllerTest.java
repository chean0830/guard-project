package com.projectguard.backend.lawyer;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(LawyerAdminController.class)
@TestPropertySource(properties = "ADMIN_SECRET=admin-secret")
class LawyerAdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private LawyerRepository lawyerRepository;

    @MockitoBean
    private LawyerCredentialDocumentRepository documentRepository;

    @Test
    void 비밀키가_없으면_목록조회는_401을_반환한다() throws Exception {
        mockMvc.perform(get("/api/admin/lawyers"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 비밀키가_맞으면_대기중_목록을_반환한다() throws Exception {
        Lawyer lawyer = new Lawyer("pending@example.com", "hash", "김변호", "법무법인 테스트", "12345");
        when(lawyerRepository.findByStatus(LawyerStatus.PENDING)).thenReturn(List.of(lawyer));
        when(documentRepository.findByLawyerId(null)).thenReturn(List.of());

        mockMvc.perform(get("/api/admin/lawyers")
                        .header("X-Admin-Secret", "admin-secret")
                        .param("status", "PENDING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].email").value("pending@example.com"))
                .andExpect(jsonPath("$[0].status").value("PENDING"));
    }

    @Test
    void 승인하면_리포지토리에_저장한다() throws Exception {
        Lawyer lawyer = new Lawyer("approve-me@example.com", "hash", "김변호", null, "12345");
        when(lawyerRepository.findById(1L)).thenReturn(Optional.of(lawyer));

        mockMvc.perform(post("/api/admin/lawyers/1/approve").header("X-Admin-Secret", "admin-secret"))
                .andExpect(status().isOk());

        verify(lawyerRepository).save(lawyer);
        org.junit.jupiter.api.Assertions.assertEquals(LawyerStatus.APPROVED, lawyer.getStatus());
    }

    @Test
    void 거절하면_사유와_함께_저장한다() throws Exception {
        Lawyer lawyer = new Lawyer("reject-me@example.com", "hash", "김변호", null, "12345");
        when(lawyerRepository.findById(2L)).thenReturn(Optional.of(lawyer));

        mockMvc.perform(post("/api/admin/lawyers/2/reject")
                        .header("X-Admin-Secret", "admin-secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LawyerAdminController.RejectRequest("서류 미비"))))
                .andExpect(status().isOk());

        org.junit.jupiter.api.Assertions.assertEquals(LawyerStatus.REJECTED, lawyer.getStatus());
        org.junit.jupiter.api.Assertions.assertEquals("서류 미비", lawyer.getRejectionReason());
    }

    @Test
    void 존재하지_않는_신청을_승인하면_404를_반환한다() throws Exception {
        when(lawyerRepository.findById(999L)).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/admin/lawyers/999/approve").header("X-Admin-Secret", "admin-secret"))
                .andExpect(status().isNotFound());
    }

    @Test
    void 비밀키가_틀리면_승인_요청도_401을_반환한다() throws Exception {
        mockMvc.perform(post("/api/admin/lawyers/1/approve").header("X-Admin-Secret", "wrong-secret"))
                .andExpect(status().isUnauthorized());
    }
}
