package com.projectguard.backend.payment;

import com.projectguard.backend.auth.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 관리자 결제 관리: 전체 결제 내역과, 사용한 이용권의 환불 요청 승인/거절.
 * 관리자 인증은 /api/admin/** 전체에 걸린 AdminAuthInterceptor가 처리한다.
 */
@RestController
@RequestMapping("/api/admin/payments")
public class AdminPaymentController {

    private final PaymentService paymentService;
    private final UserRepository userRepository;

    public AdminPaymentController(PaymentService paymentService, UserRepository userRepository) {
        this.paymentService = paymentService;
        this.userRepository = userRepository;
    }

    public record AdminPaymentItem(PaymentController.PaymentHistoryItem payment, Long userId, String userEmail) {
    }

    public record RejectRequest(String reason) {
    }

    @GetMapping
    public List<AdminPaymentItem> list(@RequestParam(required = false) PaymentStatus status) {
        return paymentService.listForAdmin(status).stream()
                .map(o -> new AdminPaymentItem(
                        PaymentController.toHistoryItem(o),
                        o.getUserId(),
                        userRepository.findById(o.getUserId()).map(u -> u.getEmail()).orElse("알 수 없음")))
                .toList();
    }

    @PostMapping("/{orderId}/refund/approve")
    public void approve(@PathVariable String orderId) {
        paymentService.approveRefund(orderId);
    }

    @PostMapping("/{orderId}/refund/reject")
    public void reject(@PathVariable String orderId, @RequestBody(required = false) RejectRequest request) {
        paymentService.rejectRefund(orderId, request != null ? request.reason() : null);
    }

    @ExceptionHandler(PaymentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String handlePayment(PaymentException e) {
        return e.getMessage();
    }
}
