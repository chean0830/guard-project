package com.projectguard.backend.risk;

/**
 * HUG(주택도시보증공사) 전세보증금반환보증 가입 기준 중 다가구(단독)주택에 해당하는 수치.
 *
 * 법령이 아니라 HUG 내부 심사 기준이라 수시로 바뀐다. 2026-10-02에 HUG 상품 안내 페이지와
 * 2026년 9월 기준 공개 정리 자료(집토스 리포트 등)를 교차 확인한 값이다. 실제 심사는 감정평가액 등
 * 다른 가격을 쓸 수도 있어, 이 서비스에서는 "가입 가능성 추정"으로만 안내한다.
 *
 * TODO: 서비스 공개 전과 HUG 기준 개정 시 khug.or.kr 상품 안내로 재확인할 것. 기획서 8번 원칙에 따라 추후 DB 기준표로 이전.
 */
public final class HugGuaranteeCriteria {

    /** 단독·다가구 주택가격 = 개별주택가격(공시가격) × 이 비율. */
    public static final double OFFICIAL_PRICE_MULTIPLIER = 1.40;

    /** (보증금 + 선순위채권 + 다른 세입자 보증금) ≤ 주택가격 × 이 비율 (담보인정비율). */
    public static final double TOTAL_RATIO_LIMIT = 0.90;

    /** 다가구: 선순위채권(근저당 등) ≤ 주택가격 × 이 비율. */
    public static final double SENIOR_DEBT_RATIO_LIMIT = 0.60;

    /** 다가구: (선순위채권 + 다른 세입자 보증금) ≤ 주택가격 × 이 비율. */
    public static final double SENIOR_DEBT_PLUS_PRIOR_DEPOSIT_RATIO_LIMIT = 0.80;

    /** 보증금 한도: 수도권(서울·인천·경기). */
    public static final long DEPOSIT_CAP_CAPITAL_AREA = 700_000_000L;

    /** 보증금 한도: 그 외 지역. */
    public static final long DEPOSIT_CAP_OTHER = 500_000_000L;

    /** 주소가 수도권(서울·인천·경기)인지. 등기부 주소 문자열 기준 추정. */
    public static boolean isCapitalArea(String address) {
        return address != null
                && (address.startsWith("서울") || address.startsWith("인천") || address.startsWith("경기"));
    }

    private HugGuaranteeCriteria() {
    }
}
