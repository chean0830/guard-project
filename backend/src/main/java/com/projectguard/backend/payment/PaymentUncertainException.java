package com.projectguard.backend.payment;

/**
 * 결제 승인 결과를 알 수 없는 경우(응답 시간 초과, 토스 서버 오류 등). 실제로는 돈이 빠져나갔을 수 있어
 * 주문을 실패로 굳히지 않고 그대로 두며, 같은 주문으로 다시 승인 요청하면 이어서 처리된다.
 */
public class PaymentUncertainException extends PaymentException {
    public PaymentUncertainException() {
        super("결제 결과를 아직 확인하지 못했어요. 잠시 후 다시 시도해주세요. 이미 결제됐다면 다시 시도할 때 이용권이 지급되고, 중복으로 결제되지 않아요.");
    }
}
