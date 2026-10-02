package com.projectguard.backend.common;

/**
 * 다가구주택에서 사용자가 입력한 "나보다 먼저 들어온 세입자 보증금 합계"를 어디서 확인했는지.
 * 다가구 전세사기는 임대인이 이 금액을 줄여 말하는 방식으로 많이 일어나므로,
 * 공식 서류로 확인한 값이 아니면 계산 결과와 상관없이 위험 신호를 띄운다.
 */
public enum PriorDepositSource {
    /** 전입세대 열람내역서·확정일자 부여현황 등 공식 서류로 확인함. */
    OFFICIAL_DOCUMENT,
    /** 임대인·중개사의 말만 들음. */
    LANDLORD_CLAIM,
    /** 확인하지 못함. */
    UNKNOWN
}
