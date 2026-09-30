package com.projectguard.backend.payment;

public enum PaymentStatus {
    /** 주문만 만들어졌고 아직 결제 승인 전 */
    READY,
    /** 토스 결제 승인 완료 → 이용권 1장 */
    PAID,
    /** 토스 승인 실패 */
    FAILED,
    /** 쓰지 않은 이용권을 회원이 직접 결제 취소함 (토스 결제 취소 완료) */
    CANCELED,
    /** 이미 사용한 이용권에 대해 회원이 환불을 요청함 — 관리자 검토 대기 */
    REFUND_REQUESTED,
    /** 관리자가 환불을 승인함 (토스 결제 취소 완료) */
    REFUNDED
}
