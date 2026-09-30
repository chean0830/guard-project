package com.projectguard.backend.payment;

public enum PaymentStatus {
    /** 주문만 만들어졌고 아직 결제 승인 전 */
    READY,
    /** 토스 결제 승인 완료 → 이용권 1장 */
    PAID,
    /** 토스 승인 실패 */
    FAILED
}
