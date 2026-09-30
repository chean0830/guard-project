package com.projectguard.backend.payment;

import com.projectguard.backend.auth.AuthService;
import com.projectguard.backend.consultation.Consultation;
import com.projectguard.backend.lawyer.Lawyer;
import com.projectguard.backend.lawyer.LawyerAuthService;
import com.projectguard.backend.lawyer.LawyerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * 결제 승인(토스 API는 목킹)과 이용권 차감, 직접 선택 상담 시작을 실제 JPA로 검증한다.
 */
@SpringBootTest
@Transactional
class PaymentServiceTest {

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private PaymentOrderRepository orderRepository;

    @Autowired
    private AuthService authService;

    @Autowired
    private LawyerAuthService lawyerAuthService;

    @Autowired
    private LawyerRepository lawyerRepository;

    @MockitoBean
    private TossPaymentsClient tossPaymentsClient;

    private Long userId;

    @BeforeEach
    void setUp() {
        String token = authService.signup("pay-" + UUID.randomUUID() + "@example.com", "password123").token();
        userId = authService.validate(token).orElseThrow().getId();
    }

    private Lawyer approvedLawyer(String name) {
        Lawyer lawyer = lawyerAuthService.signup(name + "-" + UUID.randomUUID() + "@example.com", "password123", name, null, "12345",
                List.of(new MockMultipartFile("documents", "license.pdf", "application/pdf", "%PDF-1.4 dummy".getBytes())));
        lawyer.approve();
        return lawyerRepository.save(lawyer);
    }

    private PaymentOrder paidOrder() {
        PaymentOrder order = paymentService.createOrder(userId);
        return paymentService.confirm(userId, "pk-" + UUID.randomUUID(), order.getOrderId(), 2900);
    }

    @Test
    void 주문_금액은_항상_서버가_정한_2900원이다() {
        assertEquals(2900, paymentService.createOrder(userId).getAmount());
    }

    @Test
    void 승인되면_이용권이_1장_생긴다() {
        PaymentOrder order = paymentService.createOrder(userId);

        paymentService.confirm(userId, "pk-1", order.getOrderId(), 2900);

        verify(tossPaymentsClient).confirm("pk-1", order.getOrderId(), 2900);
        assertEquals(1, paymentService.availableCredits(userId));
    }

    @Test
    void 금액이_다르면_토스에_승인요청하지_않고_실패처리한다() {
        PaymentOrder order = paymentService.createOrder(userId);

        assertThrows(PaymentException.class, () -> paymentService.confirm(userId, "pk-1", order.getOrderId(), 100));

        verify(tossPaymentsClient, never()).confirm(anyString(), anyString(), anyLong());
        assertEquals(PaymentStatus.FAILED, orderRepository.findByOrderId(order.getOrderId()).orElseThrow().getStatus());
        assertEquals(0, paymentService.availableCredits(userId));
    }

    @Test
    void 같은_결제로_승인이_두번_와도_이용권은_1장이다() {
        PaymentOrder order = paymentService.createOrder(userId);

        paymentService.confirm(userId, "pk-dup", order.getOrderId(), 2900);
        paymentService.confirm(userId, "pk-dup", order.getOrderId(), 2900);

        verify(tossPaymentsClient, times(1)).confirm("pk-dup", order.getOrderId(), 2900);
        assertEquals(1, paymentService.availableCredits(userId));
    }

    @Test
    void 다른_회원의_주문은_승인할_수_없다() {
        PaymentOrder order = paymentService.createOrder(userId);
        String otherToken = authService.signup("pay-other-" + UUID.randomUUID() + "@example.com", "password123").token();
        Long otherUserId = authService.validate(otherToken).orElseThrow().getId();

        assertThrows(PaymentException.class, () -> paymentService.confirm(otherUserId, "pk-1", order.getOrderId(), 2900));
    }

    @Test
    void 토스_승인이_거절되면_이용권이_생기지_않는다() {
        PaymentOrder order = paymentService.createOrder(userId);
        doThrow(new PaymentException("카드 한도 초과")).when(tossPaymentsClient).confirm(anyString(), anyString(), anyLong());

        PaymentException e = assertThrows(PaymentException.class, () ->
                paymentService.confirm(userId, "pk-1", order.getOrderId(), 2900));

        assertEquals("카드 한도 초과", e.getMessage());
        assertEquals(PaymentStatus.FAILED, orderRepository.findByOrderId(order.getOrderId()).orElseThrow().getStatus());
        assertEquals(0, paymentService.availableCredits(userId));
    }

    @Test
    void 이용권이_없으면_변호사를_직접_선택할_수_없다() {
        Lawyer lawyer = approvedLawyer("김선택");

        assertThrows(PaymentException.class, () -> paymentService.startDirectConsultation(userId, lawyer.getId(), "문의"));
    }

    @Test
    void 이용권으로_고른_변호사와_상담이_시작되고_이용권이_차감된다() {
        Lawyer chosen = approvedLawyer("김선택");
        approvedLawyer("이다른");
        PaymentOrder order = paidOrder();

        Consultation consultation = paymentService.startDirectConsultation(userId, chosen.getId(), "전세 문의드립니다");

        assertEquals(chosen.getId(), consultation.getLawyerId());
        assertEquals(0, paymentService.availableCredits(userId));
        assertEquals(consultation.getId(), orderRepository.findById(order.getId()).orElseThrow().getConsultationId());
    }

    @Test
    void 정지된_변호사는_선택할_수_없고_이용권은_그대로_남는다() {
        Lawyer blocked = approvedLawyer("박정지");
        blocked.block("욕설·모욕");
        lawyerRepository.save(blocked);
        paidOrder();

        assertThrows(IllegalStateException.class, () ->
                paymentService.startDirectConsultation(userId, blocked.getId(), "문의"));
        assertEquals(1, paymentService.availableCredits(userId));
    }

    @Test
    void 쓰지_않은_이용권은_바로_결제_취소된다() {
        PaymentOrder order = paidOrder();

        PaymentOrder canceled = paymentService.cancelUnused(userId, order.getOrderId());

        verify(tossPaymentsClient).cancel(eq(order.getPaymentKey()), eq(order.getOrderId()), anyString());
        assertEquals(PaymentStatus.CANCELED, canceled.getStatus());
        assertEquals(0, paymentService.availableCredits(userId));
    }

    @Test
    void 사용한_이용권은_바로_취소할_수_없고_환불_요청만_된다() {
        Lawyer lawyer = approvedLawyer("김환불");
        PaymentOrder order = paidOrder();
        paymentService.startDirectConsultation(userId, lawyer.getId(), "문의");

        assertThrows(PaymentException.class, () -> paymentService.cancelUnused(userId, order.getOrderId()));
        PaymentOrder requested = paymentService.requestRefund(userId, order.getOrderId(), "답변을 받지 못했어요");

        assertEquals(PaymentStatus.REFUND_REQUESTED, requested.getStatus());
        verify(tossPaymentsClient, never()).cancel(anyString(), anyString(), anyString());
    }

    @Test
    void 관리자가_환불을_승인하면_토스에서_취소되고_거절하면_결제완료로_돌아간다() {
        Lawyer lawyer = approvedLawyer("김승인");
        PaymentOrder first = paidOrder();
        paymentService.startDirectConsultation(userId, lawyer.getId(), "문의1");
        paymentService.requestRefund(userId, first.getOrderId(), "사유1");

        assertEquals(PaymentStatus.REFUNDED, paymentService.approveRefund(first.getOrderId()).getStatus());
        verify(tossPaymentsClient).cancel(eq(first.getPaymentKey()), eq(first.getOrderId()), anyString());

        PaymentOrder second = paidOrder();
        paymentService.startDirectConsultation(userId, lawyer.getId(), "문의2");
        paymentService.requestRefund(userId, second.getOrderId(), "사유2");
        PaymentOrder rejected = paymentService.rejectRefund(second.getOrderId(), "상담이 정상 진행됨");

        assertEquals(PaymentStatus.PAID, rejected.getStatus());
        assertEquals("상담이 정상 진행됨", rejected.getRefundRejectedReason());
    }

    @Test
    void 다른_회원의_결제는_취소할_수_없다() {
        PaymentOrder order = paidOrder();
        Long otherUserId = authService.validate(
                authService.signup("pay-other2-" + UUID.randomUUID() + "@example.com", "password123").token()).orElseThrow().getId();

        assertThrows(PaymentException.class, () -> paymentService.cancelUnused(otherUserId, order.getOrderId()));
    }
}
