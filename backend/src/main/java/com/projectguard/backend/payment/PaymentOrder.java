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

    /** 어떤 이용권인지. 이 칸이 생기기 전 결제는 모두 변호사 선택 이용권이었으므로 DB 기본값도 그것으로 둔다. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "varchar(30) default 'LAWYER_SELECTION'")
    private ProductType productType = ProductType.LAWYER_SELECTION;

    /** 이용권을 쓴 시각. 변호사 선택은 상담 시작 때, 분석 이용권은 분석 성공 때 채워진다. */
    private Instant usedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "varchar(30)")
    private PaymentStatus status = PaymentStatus.READY;

    @Column(unique = true)
    private String paymentKey;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    private Instant paidAt;

    /** 이 이용권으로 시작한 상담. null이면 아직 쓰지 않은 이용권이다. */
    private Long consultationId;

    /** 사용한 이용권의 환불 요청 사유(회원 작성). */
    @Column(length = 500)
    private String refundReason;

    private Instant refundRequestedAt;

    /** 관리자가 환불을 거절한 사유. 거절되면 상태는 다시 PAID로 돌아간다. */
    @Column(length = 500)
    private String refundRejectedReason;

    /** 결제 취소 또는 환불이 끝난 시각. */
    private Instant canceledAt;

    protected PaymentOrder() {
    }

    public PaymentOrder(String orderId, Long userId, long amount) {
        this(orderId, userId, ProductType.LAWYER_SELECTION);
        this.amount = amount;
    }

    public PaymentOrder(String orderId, Long userId, ProductType productType) {
        this.orderId = orderId;
        this.userId = userId;
        this.productType = productType;
        this.amount = productType.getPrice();
    }

    public ProductType getProductType() {
        return productType;
    }

    public Instant getUsedAt() {
        return usedAt;
    }

    public boolean isUsed() {
        return usedAt != null || consultationId != null;
    }

    public void markUsed() {
        this.usedAt = Instant.now();
    }

    public void markPaid(String paymentKey) {
        this.status = PaymentStatus.PAID;
        this.paymentKey = paymentKey;
        this.paidAt = Instant.now();
    }

    public void markFailed() {
        this.status = PaymentStatus.FAILED;
    }

    public void markCanceled() {
        this.status = PaymentStatus.CANCELED;
        this.canceledAt = Instant.now();
    }

    public void requestRefund(String reason) {
        this.status = PaymentStatus.REFUND_REQUESTED;
        this.refundReason = reason;
        this.refundRequestedAt = Instant.now();
        this.refundRejectedReason = null;
    }

    public void markRefunded() {
        this.status = PaymentStatus.REFUNDED;
        this.canceledAt = Instant.now();
    }

    public void rejectRefund(String reason) {
        this.status = PaymentStatus.PAID;
        this.refundRejectedReason = reason;
    }

    public String getRefundReason() {
        return refundReason;
    }

    public Instant getRefundRequestedAt() {
        return refundRequestedAt;
    }

    public String getRefundRejectedReason() {
        return refundRejectedReason;
    }

    public Instant getCanceledAt() {
        return canceledAt;
    }

    public void useFor(Long consultationId) {
        this.consultationId = consultationId;
        this.usedAt = Instant.now();
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
