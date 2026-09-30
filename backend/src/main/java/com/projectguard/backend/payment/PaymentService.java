package com.projectguard.backend.payment;

import com.projectguard.backend.consultation.Consultation;
import com.projectguard.backend.consultation.ConsultationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * "원하는 변호사 직접 선택" 결제. 금액은 항상 서버가 정한다 — 브라우저가 보낸 금액은 믿지 않고,
 * 승인 시점에 우리 주문의 금액과 다르면 토스에 승인 요청조차 하지 않는다(금액 위변조 방지).
 */
@Service
public class PaymentService {

    public static final long LAWYER_SELECTION_PRICE = 2_900;
    public static final String ORDER_NAME = "원하는 변호사 직접 선택 상담 1회";

    private final PaymentOrderRepository orderRepository;
    private final TossPaymentsClient tossPaymentsClient;
    private final ConsultationService consultationService;

    public PaymentService(
            PaymentOrderRepository orderRepository,
            TossPaymentsClient tossPaymentsClient,
            ConsultationService consultationService
    ) {
        this.orderRepository = orderRepository;
        this.tossPaymentsClient = tossPaymentsClient;
        this.consultationService = consultationService;
    }

    public PaymentOrder createOrder(Long userId) {
        return createOrder(userId, ProductType.LAWYER_SELECTION);
    }

    public PaymentOrder createOrder(Long userId, ProductType productType) {
        String orderId = "PG-" + UUID.randomUUID();
        return orderRepository.save(new PaymentOrder(orderId, userId, productType));
    }

    /**
     * 토스 결제창에서 돌아온 결제를 승인한다. 이미 같은 결제로 승인된 주문이면 그대로 성공 처리한다
     * (성공 페이지 새로고침 등으로 승인 요청이 두 번 와도 이용권이 두 장 생기거나 오류가 나지 않게).
     */
    @Transactional(noRollbackFor = PaymentException.class)
    public PaymentOrder confirm(Long userId, String paymentKey, String orderId, long amount) {
        PaymentOrder order = orderRepository.findByOrderId(orderId)
                .filter(o -> o.getUserId().equals(userId))
                .orElseThrow(() -> new PaymentException("주문 정보를 찾을 수 없습니다."));

        if (order.getStatus() == PaymentStatus.PAID) {
            if (order.getPaymentKey().equals(paymentKey)) {
                return order;
            }
            throw new PaymentException("이미 결제가 완료된 주문입니다.");
        }
        if (order.getStatus() == PaymentStatus.FAILED) {
            throw new PaymentException("결제에 실패한 주문입니다. 처음부터 다시 결제해주세요.");
        }
        if (amount != order.getAmount()) {
            order.markFailed();
            orderRepository.save(order);
            throw new PaymentException("결제 금액이 올바르지 않습니다.");
        }

        try {
            tossPaymentsClient.confirm(paymentKey, orderId, order.getAmount());
        } catch (PaymentException e) {
            order.markFailed();
            orderRepository.save(order);
            throw e;
        }
        order.markPaid(paymentKey);
        return orderRepository.save(order);
    }

    public long availableCredits(Long userId) {
        return availableCredits(userId, ProductType.LAWYER_SELECTION);
    }

    public long availableCredits(Long userId, ProductType productType) {
        return orderRepository.countByUserIdAndProductTypeAndStatusAndUsedAtIsNullAndConsultationIdIsNull(
                userId, productType, PaymentStatus.PAID);
    }

    /** 분석 이용권 1장을 사용 처리한다. 분석이 성공한 뒤에만 호출해서, 실패한 분석에는 이용권이 줄지 않게 한다. */
    @Transactional
    public boolean consumeAnalysisCredit(Long userId) {
        List<PaymentOrder> credits = orderRepository
                .findByUserIdAndProductTypeAndStatusAndUsedAtIsNullAndConsultationIdIsNullOrderByPaidAtAsc(
                        userId, ProductType.ANALYSIS, PaymentStatus.PAID);
        if (credits.isEmpty()) {
            return false;
        }
        PaymentOrder credit = credits.get(0);
        credit.markUsed();
        orderRepository.save(credit);
        return true;
    }

    /** 회원 본인의 결제 내역 (결제창만 열고 끝난 주문은 제외). */
    public List<PaymentOrder> history(Long userId) {
        return orderRepository.findByUserIdAndStatusNotOrderByCreatedAtDesc(userId, PaymentStatus.READY);
    }

    /** 아직 쓰지 않은 이용권은 회원이 바로 결제 취소할 수 있다(관리자 승인 불필요). */
    @Transactional
    public PaymentOrder cancelUnused(Long userId, String orderId) {
        PaymentOrder order = lockOwnOrder(userId, orderId);
        if (order.getStatus() != PaymentStatus.PAID || order.isUsed()) {
            throw new PaymentException("사용하지 않은 이용권만 바로 취소할 수 있어요. 이미 사용했다면 환불 요청을 해주세요.");
        }
        tossPaymentsClient.cancel(order.getPaymentKey(), order.getOrderId(), "미사용 이용권 결제 취소 (회원 요청)");
        order.markCanceled();
        return orderRepository.save(order);
    }

    /** 이미 상담에 쓴 이용권은 환불 요청만 할 수 있고, 관리자가 승인해야 실제로 환불된다. */
    @Transactional
    public PaymentOrder requestRefund(Long userId, String orderId, String reason) {
        if (reason == null || reason.isBlank()) {
            throw new PaymentException("환불 사유를 입력해주세요.");
        }
        if (reason.length() > 500) {
            throw new PaymentException("환불 사유는 500자 이내로 입력해주세요.");
        }
        PaymentOrder order = lockOwnOrder(userId, orderId);
        if (order.getStatus() != PaymentStatus.PAID || !order.isUsed()) {
            throw new PaymentException("사용한 이용권만 환불 요청할 수 있어요. 사용하지 않았다면 바로 결제 취소할 수 있어요.");
        }
        order.requestRefund(reason.trim());
        return orderRepository.save(order);
    }

    /** 관리자 결제 관리 목록. status가 없으면 결제창만 열고 끝난 주문을 뺀 전체. */
    public List<PaymentOrder> listForAdmin(PaymentStatus status) {
        return status != null
                ? orderRepository.findByStatusOrderByCreatedAtDesc(status)
                : orderRepository.findByStatusNotOrderByCreatedAtDesc(PaymentStatus.READY);
    }

    @Transactional
    public PaymentOrder approveRefund(String orderId) {
        PaymentOrder order = orderRepository.findByOrderIdForUpdate(orderId)
                .orElseThrow(() -> new PaymentException("주문 정보를 찾을 수 없습니다."));
        if (order.getStatus() != PaymentStatus.REFUND_REQUESTED) {
            throw new PaymentException("환불 요청 상태인 결제만 승인할 수 있습니다.");
        }
        tossPaymentsClient.cancel(order.getPaymentKey(), order.getOrderId(), "관리자 환불 승인: " + order.getRefundReason());
        order.markRefunded();
        return orderRepository.save(order);
    }

    @Transactional
    public PaymentOrder rejectRefund(String orderId, String reason) {
        PaymentOrder order = orderRepository.findByOrderIdForUpdate(orderId)
                .orElseThrow(() -> new PaymentException("주문 정보를 찾을 수 없습니다."));
        if (order.getStatus() != PaymentStatus.REFUND_REQUESTED) {
            throw new PaymentException("환불 요청 상태인 결제만 거절할 수 있습니다.");
        }
        order.rejectRefund(reason == null || reason.isBlank() ? null : reason.trim());
        return orderRepository.save(order);
    }

    private PaymentOrder lockOwnOrder(Long userId, String orderId) {
        return orderRepository.findByOrderIdForUpdate(orderId)
                .filter(o -> o.getUserId().equals(userId))
                .orElseThrow(() -> new PaymentException("주문 정보를 찾을 수 없습니다."));
    }

    /** 이용권 1장을 써서 회원이 고른 변호사와 상담을 시작한다. 이용권 차감과 상담 생성은 함께 성공하거나 함께 취소된다. */
    @Transactional
    public Consultation startDirectConsultation(Long userId, Long lawyerId, String message) {
        List<PaymentOrder> credits = orderRepository
                .findByUserIdAndProductTypeAndStatusAndUsedAtIsNullAndConsultationIdIsNullOrderByPaidAtAsc(
                        userId, ProductType.LAWYER_SELECTION, PaymentStatus.PAID);
        if (credits.isEmpty()) {
            throw new PaymentException("변호사를 직접 선택하려면 이용권이 필요합니다. 결제 후 이용해주세요.");
        }
        Consultation consultation = consultationService.startConsultationWith(userId, lawyerId, message);
        PaymentOrder credit = credits.get(0);
        credit.useFor(consultation.getId());
        orderRepository.save(credit);
        return consultation;
    }
}
