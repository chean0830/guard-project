package com.projectguard.backend.consultation;

import com.projectguard.backend.auth.AuthResult;
import com.projectguard.backend.auth.AuthService;
import com.projectguard.backend.lawyer.Lawyer;
import com.projectguard.backend.lawyer.LawyerAuthService;
import com.projectguard.backend.lawyer.LawyerRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 회원이 문의를 시작하면 승인된 변호사와 매칭되고, 서로 자신의 문의만 볼 수 있는지
 * 인메모리 H2 + 실제 JPA 리포지토리로 검증한다. 메일 발송은 MAIL_USERNAME이 없는
 * 테스트 환경에서 자동으로 건너뛰어지므로(경고 로그만 남김) 별도 목킹이 필요 없다.
 */
@SpringBootTest
@Transactional
class ConsultationServiceTest {

    @Autowired
    private ConsultationService consultationService;

    @Autowired
    private AuthService authService;

    @Autowired
    private LawyerAuthService lawyerAuthService;

    @Autowired
    private LawyerRepository lawyerRepository;

    private Long approvedLawyerId(String email) {
        Lawyer lawyer = lawyerAuthService.signup(
                email, "password123", "김변호", "법무법인 테스트", "12345", oneDocument());
        lawyer.approve();
        lawyerRepository.save(lawyer);
        return lawyer.getId();
    }

    private Long userId(String email) {
        AuthResult result = authService.signup(email, "password123");
        return authService.validate(result.token()).get().getId();
    }

    private List<MultipartFile> oneDocument() {
        return List.of(new MockMultipartFile("documents", "license.pdf", "application/pdf", "%PDF-1.4 dummy".getBytes()));
    }

    @Test
    void 문의를_시작하면_승인된_변호사와_매칭되고_첫_메시지가_저장된다() {
        Long lawyerId = approvedLawyerId("match1@example.com");
        Long userId = userId("user1@example.com");

        Consultation consultation = consultationService.startConsultation(userId, "보증금을 못 받고 있어요");

        assertEquals(lawyerId, consultation.getLawyerId());
        assertEquals(userId, consultation.getUserId());
        assertEquals(1, consultationService.getThreadAsUser(consultation.getId(), userId).size());
    }

    @Test
    void 승인된_변호사가_없으면_문의_시작에_실패한다() {
        Long userId = userId("nolaywer@example.com");
        assertThrows(IllegalStateException.class, () -> consultationService.startConsultation(userId, "문의합니다"));
    }

    @Test
    void 문의_내용이_비어있으면_실패한다() {
        approvedLawyerId("match2@example.com");
        Long userId = userId("user2@example.com");
        assertThrows(IllegalArgumentException.class, () -> consultationService.startConsultation(userId, "  "));
    }

    @Test
    void 변호사가_답장하면_대화방에_쌓인다() {
        Long lawyerId = approvedLawyerId("match3@example.com");
        Long userId = userId("user3@example.com");
        Consultation consultation = consultationService.startConsultation(userId, "문의합니다");

        consultationService.postMessage(consultation.getId(), SenderType.LAWYER, lawyerId, "네, 상황을 말씀해주세요");

        List<ConsultationMessage> thread = consultationService.getThreadAsUser(consultation.getId(), userId);
        assertEquals(2, thread.size());
        assertEquals(SenderType.LAWYER, thread.get(1).getSenderType());
    }

    @Test
    void 다른_회원은_남의_문의를_볼_수_없다() {
        approvedLawyerId("match4@example.com");
        Long userId = userId("owner@example.com");
        Long otherUserId = userId("other@example.com");
        Consultation consultation = consultationService.startConsultation(userId, "문의합니다");

        assertThrows(ConsultationAccessDeniedException.class,
                () -> consultationService.getThreadAsUser(consultation.getId(), otherUserId));
    }

    @Test
    void 매칭되지_않은_변호사는_문의를_볼_수_없다() {
        Long lawyerId1 = approvedLawyerId("match5@example.com");
        Long lawyerId2 = approvedLawyerId("other-lawyer@example.com");
        Long userId = userId("user5@example.com");
        Consultation consultation = consultationService.startConsultation(userId, "문의합니다");

        Long notMatchedLawyerId = consultation.getLawyerId().equals(lawyerId1) ? lawyerId2 : lawyerId1;

        assertThrows(ConsultationAccessDeniedException.class,
                () -> consultationService.getThreadAsLawyer(consultation.getId(), notMatchedLawyerId));
    }

    @Test
    void 회원이_읽지_않은_변호사_메시지_수를_셀_수_있다() {
        Long lawyerId = approvedLawyerId("match6@example.com");
        Long userId = userId("user6@example.com");
        Consultation consultation = consultationService.startConsultation(userId, "문의합니다");
        consultationService.postMessage(consultation.getId(), SenderType.LAWYER, lawyerId, "답변입니다");

        assertEquals(1, consultationService.unreadCountForUser(consultation.getId()));

        consultationService.getThreadAsUser(consultation.getId(), userId);

        assertEquals(0, consultationService.unreadCountForUser(consultation.getId()));
    }

    @Test
    void 회원의_문의_목록에_새로_시작한_문의가_보인다() {
        approvedLawyerId("match7@example.com");
        Long userId = userId("user7@example.com");
        Consultation consultation = consultationService.startConsultation(userId, "문의합니다");

        List<Consultation> list = consultationService.listForUser(userId);
        assertTrue(list.stream().anyMatch(c -> c.getId().equals(consultation.getId())));
    }
}
