package com.projectguard.backend.payment;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * "원하는 변호사 직접 선택" 이용권 1장 = 결제 1건. 결제가 승인(PAID)되면 이용권이 생기고,
 * 회원이 변호사를 골라 상담을 시작하면 consultationId가 채워지면서 사용 처리된다.
 */
@Entity
@Table(name = "payment_orders")
public class PaymentOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 토스에 넘기는 주문번호. 우리가 무작위로 만들어 추측할 수 없게 한다. */
    @Column(nullable = false, unique = true)
    private String orderId;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private long amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status = PaymentStatus.READY;

    @Column(unique = true)
    private String paymentKey;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    private Instant paidAt;

    /** 이 이용권으로 시작한 상담. null이면 아직 쓰지 않은 이용권이다. */
    private Long consultationId;

    protected PaymentOrder() {
    }

    public PaymentOrder(String orderId, Long userId, long amount) {
        this.orderId = orderId;
        this.userId = userId;
        this.amount = amount;
    }

    public void markPaid(String paymentKey) {
        this.status = PaymentStatus.PAID;
        this.paymentKey = paymentKey;
        this.paidAt = Instant.now();
    }

    public void markFailed() {
        this.status = PaymentStatus.FAILED;
    }

    public void useFor(Long consultationId) {
        this.consultationId = consultationId;
    }

    public Long getId() {
        return id;
    }

    public String getOrderId() {
        return orderId;
    }

    public Long getUserId() {
        return userId;
    }

    public long getAmount() {
        return amount;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public String getPaymentKey() {
        return paymentKey;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getPaidAt() {
        return paidAt;
    }

    public Long getConsultationId() {
        return consultationId;
    }
}
