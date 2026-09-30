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
        String orderId = "PG-" + UUID.randomUUID();
        return orderRepository.save(new PaymentOrder(orderId, userId, LAWYER_SELECTION_PRICE));
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
        return orderRepository.countByUserIdAndStatusAndConsultationIdIsNull(userId, PaymentStatus.PAID);
    }

    /** 이용권 1장을 써서 회원이 고른 변호사와 상담을 시작한다. 이용권 차감과 상담 생성은 함께 성공하거나 함께 취소된다. */
    @Transactional
    public Consultation startDirectConsultation(Long userId, Long lawyerId, String message) {
        List<PaymentOrder> credits = orderRepository
                .findByUserIdAndStatusAndConsultationIdIsNullOrderByPaidAtAsc(userId, PaymentStatus.PAID);
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
