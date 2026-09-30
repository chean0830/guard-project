package com.projectguard.backend.auth;

import com.projectguard.backend.api.AnalysisAccessService;
import com.projectguard.backend.api.AnalysisPaymentRequiredException;
import com.projectguard.backend.lawyer.LawyerAuthService;
import com.projectguard.backend.payment.PaymentOrder;
import com.projectguard.backend.payment.PaymentService;
import com.projectguard.backend.payment.ProductType;
import com.projectguard.backend.payment.TossPaymentsClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 남용 방지(무료 분석 IP당 5회, 비밀번호 찾기 메일 5회), 자격 서류 형식 제한, 회원 탈퇴. */
@SpringBootTest
@Transactional
class AbuseAndWithdrawalTest {

    @Autowired
    private AnalysisAccessService analysisAccessService;

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private AuthService authService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordResetService passwordResetService;

    @Autowired
    private AccountDeletionService accountDeletionService;

    @Autowired
    private LawyerAuthService lawyerAuthService;

    @MockitoBean
    private TossPaymentsClient tossPaymentsClient;

    private String newUserToken() {
        return authService.signup("analysis-" + UUID.randomUUID() + "@example.com", "password123").token();
    }

    @Test
    void 로그인하지_않으면_분석할_수_없다() {
        assertThrows(InvalidCredentialsException.class, () -> analysisAccessService.authorize(null));
    }

    @Test
    void 아이디마다_무료_5회_뒤에는_결제를_요구한다() {
        String token = newUserToken();
        for (int i = 0; i < 5; i++) {
            assertFalse(analysisAccessService.authorize(token).paid());
        }
        assertThrows(AnalysisPaymentRequiredException.class, () -> analysisAccessService.authorize(token));

        // 다른 아이디는 따로 5회
        assertFalse(analysisAccessService.authorize(newUserToken()).paid());
    }

    @Test
    void 무료를_다_쓰면_결제한_분석_이용권을_쓰고_성공한_뒤에만_차감한다() {
        String token = newUserToken();
        Long userId = authService.validate(token).orElseThrow().getId();
        for (int i = 0; i < 5; i++) {
            analysisAccessService.authorize(token);
        }
        PaymentOrder order = paymentService.createOrder(userId, ProductType.ANALYSIS);
        assertEquals(990, order.getAmount());
        paymentService.confirm(userId, "pk-" + UUID.randomUUID(), order.getOrderId(), 990);

        AnalysisAccessService.Access access = analysisAccessService.authorize(token);
        assertTrue(access.paid());
        analysisAccessService.complete(access);
        assertEquals(0, paymentService.availableCredits(userId, ProductType.ANALYSIS));
        assertThrows(AnalysisPaymentRequiredException.class, () -> analysisAccessService.authorize(token));
    }

    @Test
    void 탈퇴_후_같은_이메일로_다시_가입해도_무료_횟수가_다시_생기지_않는다() {
        String email = "rejoin-" + UUID.randomUUID() + "@example.com";
        String token = authService.signup(email, "password123").token();
        for (int i = 0; i < 5; i++) {
            analysisAccessService.authorize(token);
        }
        accountDeletionService.withdraw(authService.validate(token).orElseThrow().getId(), "password123", null);

        String again = authService.signup(email, "password123").token();
        assertThrows(AnalysisPaymentRequiredException.class, () -> analysisAccessService.authorize(again));
    }

    @Test
    void 비밀번호_찾기_메일은_같은_이메일로_하루_5번까지만_보낸다() {
        String email = "limit-" + UUID.randomUUID() + "@example.com";
        for (int i = 0; i < 5; i++) {
            passwordResetService.requestReset(email, "USER");
        }
        assertThrows(IllegalStateException.class, () -> passwordResetService.requestReset(email, "USER"));
    }

    @Test
    void 자격_서류는_PDF와_JPG만_받는다() {
        MockMultipartFile fakePdf = new MockMultipartFile("documents", "license.pdf", "application/pdf",
                "<html><script>alert(1)</script></html>".getBytes());

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> lawyerAuthService.signup(
                "doc-" + UUID.randomUUID() + "@example.com", "password123", "김변호", null, "12345", List.of(fakePdf)));
        assertTrue(e.getMessage().contains("PDF 또는 JPG"));

        MockMultipartFile png = new MockMultipartFile("documents", "license.png", "image/png",
                new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0});
        assertThrows(IllegalArgumentException.class, () -> lawyerAuthService.signup(
                "doc-" + UUID.randomUUID() + "@example.com", "password123", "김변호", null, "12345", List.of(png)));

        MockMultipartFile jpg = new MockMultipartFile("documents", "license.jpg", "text/plain",
                new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0, 0, 0, 0, 0, 0, 0});
        lawyerAuthService.signup("doc-" + UUID.randomUUID() + "@example.com", "password123", "김변호", null, "12345", List.of(jpg));
    }

    @Test
    void 회원이_탈퇴하면_계정과_세션이_사라지고_같은_이메일로_다시_가입할_수_있다() {
        String email = "bye-" + UUID.randomUUID() + "@example.com";
        String token = authService.signup(email, "password123").token();
        Long userId = authService.validate(token).orElseThrow().getId();

        assertThrows(InvalidCredentialsException.class, () -> accountDeletionService.withdraw(userId, "wrong", null));
        accountDeletionService.withdraw(userId, "password123", null);

        assertTrue(userRepository.findByEmail(email).isEmpty());
        assertTrue(authService.validate(token).isEmpty());
        authService.signup(email, "password123");
    }

    @Test
    void 쓰지_않은_이용권이_있으면_탈퇴할_수_없다() {
        String token = authService.signup("credit-" + UUID.randomUUID() + "@example.com", "password123").token();
        Long userId = authService.validate(token).orElseThrow().getId();
        PaymentOrder order = paymentService.createOrder(userId);
        paymentService.confirm(userId, "pk-" + UUID.randomUUID(), order.getOrderId(), 2900);

        assertThrows(IllegalStateException.class, () -> accountDeletionService.withdraw(userId, "password123", null));
    }

    @Test
    void 소셜_전용_계정은_확인_문구로_탈퇴한다() {
        AuthResult result = authService.oauthLogin("KAKAO", "bye-" + UUID.randomUUID(), "social-" + UUID.randomUUID() + "@example.com", true);
        Long userId = authService.validate(result.token()).orElseThrow().getId();

        assertThrows(IllegalArgumentException.class, () -> accountDeletionService.withdraw(userId, null, "네"));
        accountDeletionService.withdraw(userId, null, "탈퇴");
        assertTrue(userRepository.findById(userId).isEmpty());
    }
}
