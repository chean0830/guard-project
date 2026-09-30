package com.projectguard.backend.payment;

import com.projectguard.backend.auth.AuthService;
import com.projectguard.backend.auth.InvalidCredentialsException;
import com.projectguard.backend.auth.User;
import com.projectguard.backend.consultation.Consultation;
import com.projectguard.backend.consultation.ConsultationService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

/**
 * 변호사 직접 선택 결제 흐름: 주문 생성 → (브라우저에서 토스 결제창) → 승인 → 이용권으로 변호사 목록 조회
 * → 원하는 변호사와 상담 시작. 모두 회원 세션이 필요하고, 변호사 목록은 이용권이 있어야 볼 수 있다.
 */
@RestController
public class PaymentController {

    private final PaymentService paymentService;
    private final ConsultationService consultationService;
    private final AuthService authService;

    public PaymentController(PaymentService paymentService, ConsultationService consultationService, AuthService authService) {
        this.paymentService = paymentService;
        this.consultationService = consultationService;
        this.authService = authService;
    }

    public record OrderRequest(ProductType productType) {
    }

    public record ConfirmResponse(String productType, long credits) {
    }

    public record OrderResponse(String orderId, long amount, String orderName) {
    }

    public record ConfirmRequest(String paymentKey, String orderId, Long amount) {
    }

    public record CreditsResponse(long credits, long price) {
    }

    public record LawyerCard(
            Long id, String name, String lawFirm, String specialties, String introduction,
            String headline, Integer careerYears, String feeInfo, String achievements
    ) {
    }

    /** 결제 내역 한 줄. usable: 아직 쓰지 않아 바로 취소 가능한 이용권인지. */
    public record PaymentHistoryItem(
            String orderId, String productType, String orderName, long amount, String status, Instant paidAt, Instant canceledAt, boolean used,
            Long consultationId, String refundReason, String refundRejectedReason
    ) {
    }

    public record RefundRequest(String reason) {
    }

    public record DirectConsultationRequest(Long lawyerId, String message) {
    }

    public record DirectConsultationResponse(Long id) {
    }

    @PostMapping("/api/payments/orders")
    public OrderResponse createOrder(
            @RequestBody(required = false) OrderRequest request,
            @RequestHeader(value = "Authorization", required = false) String authorization
    ) {
        ProductType product = request != null && request.productType() != null ? request.productType() : ProductType.LAWYER_SELECTION;
        PaymentOrder order = paymentService.createOrder(requireUser(authorization).getId(), product);
        return new OrderResponse(order.getOrderId(), order.getAmount(), product.getOrderName());
    }

    @PostMapping("/api/payments/confirm")
    public ConfirmResponse confirm(
            @RequestBody ConfirmRequest request,
            @RequestHeader(value = "Authorization", required = false) String authorization
    ) {
        User user = requireUser(authorization);
        if (request.paymentKey() == null || request.orderId() == null || request.amount() == null) {
            throw new PaymentException("결제 정보가 올바르지 않습니다.");
        }
        PaymentOrder order = paymentService.confirm(user.getId(), request.paymentKey(), request.orderId(), request.amount());
        return new ConfirmResponse(order.getProductType().name(), paymentService.availableCredits(user.getId(), order.getProductType()));
    }

    @GetMapping("/api/payments/credits")
    public CreditsResponse credits(@RequestHeader(value = "Authorization", required = false) String authorization) {
        return new CreditsResponse(
                paymentService.availableCredits(requireUser(authorization).getId()),
                PaymentService.LAWYER_SELECTION_PRICE
        );
    }

    @GetMapping("/api/payments/history")
    public List<PaymentHistoryItem> history(@RequestHeader(value = "Authorization", required = false) String authorization) {
        return paymentService.history(requireUser(authorization).getId()).stream().map(PaymentController::toHistoryItem).toList();
    }

    @PostMapping("/api/payments/{orderId}/cancel")
    public PaymentHistoryItem cancel(
            @PathVariable String orderId,
            @RequestHeader(value = "Authorization", required = false) String authorization
    ) {
        return toHistoryItem(paymentService.cancelUnused(requireUser(authorization).getId(), orderId));
    }

    @PostMapping("/api/payments/{orderId}/refund-request")
    public PaymentHistoryItem requestRefund(
            @PathVariable String orderId,
            @RequestBody RefundRequest request,
            @RequestHeader(value = "Authorization", required = false) String authorization
    ) {
        return toHistoryItem(paymentService.requestRefund(requireUser(authorization).getId(), orderId, request.reason()));
    }

    static PaymentHistoryItem toHistoryItem(PaymentOrder o) {
        return new PaymentHistoryItem(
                o.getOrderId(), o.getProductType().name(), o.getProductType().getOrderName(), o.getAmount(), o.getStatus().name(),
                o.getPaidAt(), o.getCanceledAt(), o.isUsed(),
                o.getConsultationId(), o.getRefundReason(), o.getRefundRejectedReason()
        );
    }

    @GetMapping("/api/lawyers/directory")
    public List<LawyerCard> directory(@RequestHeader(value = "Authorization", required = false) String authorization) {
        User user = requireUser(authorization);
        if (paymentService.availableCredits(user.getId()) == 0) {
            throw new CreditRequiredException();
        }
        return consultationService.listAvailableLawyersFor(user.getId()).stream()
                .map(l -> new LawyerCard(
                        l.getId(), l.getName(), l.getLawFirm(), l.getSpecialties(), l.getIntroduction(),
                        l.getHeadline(), l.getCareerYears(), l.getFeeInfo(), l.getAchievements()))
                .toList();
    }

    @PostMapping("/api/consultations/direct")
    public DirectConsultationResponse startDirect(
            @RequestBody DirectConsultationRequest request,
            @RequestHeader(value = "Authorization", required = false) String authorization
    ) {
        User user = requireUser(authorization);
        if (request.lawyerId() == null) {
            throw new IllegalArgumentException("상담할 변호사를 선택해주세요.");
        }
        Consultation consultation = paymentService.startDirectConsultation(user.getId(), request.lawyerId(), request.message());
        return new DirectConsultationResponse(consultation.getId());
    }

    private User requireUser(String authorization) {
        String token = authorization != null && authorization.startsWith("Bearer ")
                ? authorization.substring("Bearer ".length())
                : null;
        return authService.validate(token).orElseThrow(() -> new InvalidCredentialsException("로그인이 필요합니다."));
    }

    static class CreditRequiredException extends RuntimeException {
        CreditRequiredException() {
            super("변호사 목록은 결제 후 확인할 수 있습니다.");
        }
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public String handleInvalidCredentials(InvalidCredentialsException e) {
        return e.getMessage();
    }

    @ExceptionHandler(CreditRequiredException.class)
    @ResponseStatus(HttpStatus.PAYMENT_REQUIRED)
    public String handleCreditRequired(CreditRequiredException e) {
        return e.getMessage();
    }

    @ExceptionHandler(PaymentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String handlePayment(PaymentException e) {
        return e.getMessage();
    }

    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String handleBadRequest(RuntimeException e) {
        return e.getMessage();
    }
}
