package com.projectguard.backend.moderation;

import com.projectguard.backend.auth.AccountBlockedException;
import com.projectguard.backend.auth.AuthResult;
import com.projectguard.backend.auth.AuthService;
import com.projectguard.backend.consultation.Consultation;
import com.projectguard.backend.consultation.ConsultationAccessDeniedException;
import com.projectguard.backend.consultation.ConsultationService;
import com.projectguard.backend.consultation.SenderType;
import com.projectguard.backend.lawyer.Lawyer;
import com.projectguard.backend.lawyer.LawyerAuthResult;
import com.projectguard.backend.lawyer.LawyerAuthService;
import com.projectguard.backend.lawyer.LawyerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 신고 접수 → 관리자 처리 → 계정 정지 → 로그인/세션/매칭/메시지 차단까지 실제 JPA로 검증한다.
 */
@SpringBootTest
@Transactional
class ModerationServiceTest {

    @Autowired
    private ModerationService moderationService;

    @Autowired
    private ConsultationService consultationService;

    @Autowired
    private AuthService authService;

    @Autowired
    private LawyerAuthService lawyerAuthService;

    @Autowired
    private LawyerRepository lawyerRepository;

    @Autowired
    private ReportRepository reportRepository;

    private String userToken;
    private Long userId;
    private Long lawyerId;
    private Long consultationId;

    @BeforeEach
    void setUp() {
        // 다른 테스트가 남긴 승인 변호사가 매칭되지 않도록, 이 테스트의 변호사 한 명만 승인 상태로 둔다.
        lawyerRepository.findAll().forEach(l -> l.reject("테스트 격리"));
        Lawyer lawyer = lawyerAuthService.signup("mod-lawyer@example.com", "password123", "김변호", null, "12345",
                List.of(new MockMultipartFile("documents", "license.pdf", "application/pdf", "%PDF-1.4 dummy".getBytes())));
        lawyer.approve();
        lawyerRepository.save(lawyer);
        lawyerId = lawyer.getId();

        AuthResult user = authService.signup("mod-user@example.com", "password123");
        userToken = user.token();
        userId = authService.validate(userToken).orElseThrow().getId();

        Consultation consultation = consultationService.startConsultation(userId, "전세 계약 문의드립니다");
        consultationId = consultation.getId();
    }

    @Test
    void 회원이_변호사를_신고하면_대상은_해당_대화방의_변호사로_기록된다() {
        Report report = moderationService.submitReport(
                consultationId, SenderType.USER, userId, ReportReason.MONEY_REQUEST, "착수금을 개인 계좌로 보내라고 함");

        assertEquals(SenderType.LAWYER, report.getTargetType());
        assertEquals(lawyerId, report.getTargetId());
        assertEquals(ReportStatus.PENDING, report.getStatus());
    }

    @Test
    void 신고_사유가_없으면_거부한다() {
        assertThrows(IllegalArgumentException.class, () ->
                moderationService.submitReport(consultationId, SenderType.USER, userId, null, null));
    }

    @Test
    void 대화방_당사자가_아니면_신고할_수_없다() {
        Long otherUserId = authService.validate(authService.signup("mod-other@example.com", "password123").token())
                .orElseThrow().getId();

        assertThrows(ConsultationAccessDeniedException.class, () ->
                moderationService.submitReport(consultationId, SenderType.USER, otherUserId, ReportReason.ABUSIVE_LANGUAGE, null));
    }

    @Test
    void 검토중인_신고가_있으면_같은_대화방에서_중복_신고할_수_없다() {
        moderationService.submitReport(consultationId, SenderType.USER, userId, ReportReason.ABUSIVE_LANGUAGE, null);

        assertThrows(IllegalStateException.class, () ->
                moderationService.submitReport(consultationId, SenderType.USER, userId, ReportReason.MONEY_REQUEST, null));
    }

    @Test
    void 변호사가_회원을_신고해_처리되면_회원의_기존_세션과_로그인이_모두_막힌다() {
        Report report = moderationService.submitReport(
                consultationId, SenderType.LAWYER, lawyerId, ReportReason.ABUSIVE_LANGUAGE, null);

        moderationService.actionReport(report.getId());

        assertEquals(ReportStatus.ACTIONED, reportRepository.findById(report.getId()).orElseThrow().getStatus());
        assertTrue(authService.validate(userToken).isEmpty());
        AccountBlockedException e = assertThrows(AccountBlockedException.class, () ->
                authService.login("mod-user@example.com", "password123"));
        assertTrue(e.getMessage().contains("욕설·모욕"));
    }

    @Test
    void 정지된_변호사는_로그인과_새_문의_매칭에서_제외되고_회원은_더_이상_메시지를_보낼_수_없다() {
        LawyerAuthResult session = lawyerAuthService.login("mod-lawyer@example.com", "password123");
        Report report = moderationService.submitReport(
                consultationId, SenderType.USER, userId, ReportReason.MONEY_REQUEST, null);

        moderationService.actionReport(report.getId());

        assertTrue(lawyerAuthService.validate(session.token()).isEmpty());
        assertThrows(AccountBlockedException.class, () ->
                lawyerAuthService.login("mod-lawyer@example.com", "password123"));
        assertThrows(IllegalStateException.class, () ->
                consultationService.startConsultation(userId, "새 문의"));
        assertThrows(IllegalStateException.class, () ->
                consultationService.postMessage(consultationId, SenderType.USER, userId, "계세요?"));
        assertTrue(consultationService.isCounterpartBlocked(
                consultationService.requireConsultation(consultationId), SenderType.USER));
    }

    @Test
    void 기각하면_계정은_그대로_유지된다() {
        Report report = moderationService.submitReport(
                consultationId, SenderType.USER, userId, ReportReason.ABUSIVE_LANGUAGE, null);

        moderationService.dismissReport(report.getId());

        assertEquals(ReportStatus.DISMISSED, reportRepository.findById(report.getId()).orElseThrow().getStatus());
        assertFalse(lawyerRepository.findById(lawyerId).orElseThrow().isBlocked());
        assertThrows(IllegalStateException.class, () -> moderationService.actionReport(report.getId()));
    }

    @Test
    void 정지를_해제하면_다시_로그인할_수_있다() {
        moderationService.blockUser(userId, "관리자 직권");
        assertThrows(AccountBlockedException.class, () -> authService.login("mod-user@example.com", "password123"));

        moderationService.unblockUser(userId);

        assertTrue(authService.validate(authService.login("mod-user@example.com", "password123").token()).isPresent());
    }
}
