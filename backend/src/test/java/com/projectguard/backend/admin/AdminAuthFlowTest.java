package com.projectguard.backend.admin;

import com.projectguard.backend.auth.AuthService;
import com.projectguard.backend.auth.InvalidCredentialsException;
import com.projectguard.backend.lawyer.LawyerAuthService;
import com.projectguard.backend.lawyer.LawyerRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 관리자 로그인 → 세션 토큰으로 /api/admin/** 접근, 그리고 개발용 시드 계정(회원/승인된 변호사)이
 * 서버 시작 시 실제로 로그인 가능한 상태로 만들어지는지 검증한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = {
        "ADMIN_EMAIL=admin@example.com",
        "ADMIN_PASSWORD=admin-password",
        "ADMIN_SECRET=",
        "SEED_USER_EMAIL=seed-user@example.com",
        "SEED_USER_PASSWORD=password123",
        "SEED_LAWYER_EMAIL=seed-lawyer@example.com",
        "SEED_LAWYER_PASSWORD=password123",
})
class AdminAuthFlowTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AdminAuthService adminAuthService;

    @Autowired
    private AuthService authService;

    @Autowired
    private LawyerAuthService lawyerAuthService;

    @Autowired
    private LawyerRepository lawyerRepository;

    @Test
    void 관리자_계정이_맞으면_토큰을_발급하고_틀리면_거부한다() {
        String token = adminAuthService.login("admin@example.com", "admin-password");
        assertTrue(adminAuthService.isValid(token));

        assertThrows(InvalidCredentialsException.class, () -> adminAuthService.login("admin@example.com", "wrong"));
        assertThrows(InvalidCredentialsException.class, () -> adminAuthService.login("other@example.com", "admin-password"));
    }

    @Test
    void 로그아웃하면_토큰이_무효가_된다() {
        String token = adminAuthService.login("admin@example.com", "admin-password");
        adminAuthService.logout(token);
        assertFalse(adminAuthService.isValid(token));
    }

    @Test
    void 관리자_토큰이_있어야_관리자_API를_쓸_수_있다() throws Exception {
        mockMvc.perform(get("/api/admin/reports")).andExpect(status().isUnauthorized());

        String token = adminAuthService.login("admin@example.com", "admin-password");
        mockMvc.perform(get("/api/admin/reports").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void 회원_토큰으로는_관리자_API를_쓸_수_없다() throws Exception {
        String userToken = authService.login("seed-user@example.com", "password123").token();

        mockMvc.perform(get("/api/admin/members/users").header("Authorization", "Bearer " + userToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 로그인_API는_관리자_인증_없이_호출할_수_있다() throws Exception {
        mockMvc.perform(post("/api/admin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"admin@example.com\",\"password\":\"admin-password\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void 시드_회원과_승인된_변호사로_바로_로그인할_수_있다() {
        assertTrue(authService.validate(authService.login("seed-user@example.com", "password123").token()).isPresent());
        assertTrue(lawyerAuthService.validate(lawyerAuthService.login("seed-lawyer@example.com", "password123").token()).isPresent());
        assertFalse(lawyerRepository.findByEmail("seed-lawyer@example.com").orElseThrow().isEmailNotificationsEnabled());
    }
}
