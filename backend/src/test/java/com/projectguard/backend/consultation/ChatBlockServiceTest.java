package com.projectguard.backend.consultation;

import com.projectguard.backend.auth.AuthService;
import com.projectguard.backend.lawyer.Lawyer;
import com.projectguard.backend.lawyer.LawyerAuthService;
import com.projectguard.backend.lawyer.LawyerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 회원·변호사 사이의 개인 차단: 메시지 송수신 차단, 매칭 제외, 목록·해제. */
@SpringBootTest
@Transactional
class ChatBlockServiceTest {

    @Autowired
    private ChatBlockService blockService;

    @Autowired
    private ConsultationService consultationService;

    @Autowired
    private AuthService authService;

    @Autowired
    private LawyerAuthService lawyerAuthService;

    @Autowired
    private LawyerRepository lawyerRepository;

    private Long userId;
    private Long lawyerId;
    private Long otherLawyerId;
    private Consultation consultation;

    @BeforeEach
    void setUp() {
        lawyerRepository.findAll().forEach(l -> l.reject("테스트 격리"));
        lawyerId = approvedLawyer().getId();
        String token = authService.signup("block-user-" + UUID.randomUUID() + "@example.com", "password123").token();
        userId = authService.validate(token).orElseThrow().getId();
        consultation = consultationService.startConsultationWith(userId, lawyerId, "문의드립니다");
        otherLawyerId = approvedLawyer().getId();
    }

    private Lawyer approvedLawyer() {
        Lawyer lawyer = lawyerAuthService.signup("block-lawyer-" + UUID.randomUUID() + "@example.com", "password123", "김변호", null, "12345",
                List.of(new MockMultipartFile("documents", "license.pdf", "application/pdf", "%PDF-1.4 dummy".getBytes())));
        lawyer.approve();
        return lawyerRepository.save(lawyer);
    }

    @Test
    void 회원이_변호사를_차단하면_양쪽_모두_메시지를_보낼_수_없다() {
        blockService.blockCounterpart(consultation.getId(), SenderType.USER, userId);

        IllegalStateException mine = assertThrows(IllegalStateException.class, () ->
                consultationService.postMessage(consultation.getId(), SenderType.USER, userId, "안녕하세요"));
        assertTrue(mine.getMessage().contains("차단한 상대"));
        assertThrows(IllegalStateException.class, () ->
                consultationService.postMessage(consultation.getId(), SenderType.LAWYER, lawyerId, "답변드립니다"));

        assertEquals("BLOCKED_BY_ME", consultationService.blockStateFor(consultation, SenderType.USER));
        assertEquals("BLOCKED_ME", consultationService.blockStateFor(consultation, SenderType.LAWYER));
    }

    @Test
    void 변호사가_회원을_차단하면_그_변호사는_그_회원의_새_문의에_매칭되지_않는다() {
        blockService.blockCounterpart(consultation.getId(), SenderType.LAWYER, lawyerId);

        for (int i = 0; i < 10; i++) {
            assertEquals(otherLawyerId, consultationService.startConsultation(userId, "새 문의 " + i).getLawyerId());
        }
        assertThrows(IllegalStateException.class, () -> consultationService.startConsultationWith(userId, lawyerId, "직접 선택"));
        assertFalse(consultationService.listAvailableLawyersFor(userId).stream().anyMatch(l -> l.getId().equals(lawyerId)));
    }

    @Test
    void 차단을_해제하면_다시_대화할_수_있다() {
        ChatBlock block = blockService.blockCounterpart(consultation.getId(), SenderType.USER, userId);
        assertEquals(1, blockService.listMine(SenderType.USER, userId).size());

        blockService.unblock(block.getId(), SenderType.USER, userId);

        assertEquals(0, blockService.listMine(SenderType.USER, userId).size());
        assertDoesNotThrow(() -> consultationService.postMessage(consultation.getId(), SenderType.USER, userId, "다시 문의드려요"));
    }

    @Test
    void 같은_상대를_두_번_차단해도_한_건만_남는다() {
        blockService.blockCounterpart(consultation.getId(), SenderType.USER, userId);
        blockService.blockCounterpart(consultation.getId(), SenderType.USER, userId);

        assertEquals(1, blockService.listMine(SenderType.USER, userId).size());
    }

    @Test
    void 상대가_건_차단은_내가_풀_수_없다() {
        ChatBlock byLawyer = blockService.blockCounterpart(consultation.getId(), SenderType.LAWYER, lawyerId);

        assertThrows(NoSuchElementException.class, () -> blockService.unblock(byLawyer.getId(), SenderType.USER, userId));
    }

    @Test
    void 대화방_당사자가_아니면_차단할_수_없다() {
        assertThrows(ConsultationAccessDeniedException.class, () ->
                blockService.blockCounterpart(consultation.getId(), SenderType.LAWYER, otherLawyerId));
    }
}
