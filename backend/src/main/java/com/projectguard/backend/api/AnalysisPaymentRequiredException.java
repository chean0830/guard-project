package com.projectguard.backend.api;

/** 무료 분석(아이디당 5회)을 다 썼고 분석 이용권도 없는 경우. 프론트엔드는 이 응답(402)을 보고 990원 결제를 안내한다. */
public class AnalysisPaymentRequiredException extends RuntimeException {
    public AnalysisPaymentRequiredException() {
        super("무료 분석 " + AnalysisAccessService.FREE_ANALYSES_PER_ACCOUNT + "회를 모두 사용했어요. 990원을 결제하면 1회 더 분석할 수 있어요.");
    }
}
