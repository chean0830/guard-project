package com.projectguard.backend.risk.rules;

import com.projectguard.backend.market.OfficialHousePrice;
import com.projectguard.backend.registry.LandRegistryComparison;
import com.projectguard.backend.risk.RiskAssessmentInput;

/**
 * 다가구 규칙들이 함께 쓰는 계산 기준.
 *
 * @param price 비율 계산의 기준 가격. 사용자가 입력한 건물 시세를 우선하고, 없으면 공시가격을 쓴다.
 *              공시가격은 보통 실제 시세보다 낮아 비율이 높게(보수적으로) 나온다.
 * @param label 설명 문구에 넣을 기준 가격 표현
 */
record MultiHouseholdPriceBasis(long price, String label) {

    /** 기준 가격을 알 수 없으면 null. */
    static MultiHouseholdPriceBasis of(RiskAssessmentInput input) {
        if (input.marketPrice() != null && input.marketPrice() > 0) {
            return new MultiHouseholdPriceBasis(input.marketPrice(),
                    String.format("건물 시세 %,d원(입력값)", input.marketPrice()));
        }
        OfficialHousePrice official = input.officialHousePrice();
        if (official != null && official.price() > 0) {
            return new MultiHouseholdPriceBasis(official.price(), String.format(
                    "공시가격 %,d원(%d년 기준, 실제 시세보다 낮은 경우가 많아 보수적으로 계산)",
                    official.price(), official.year()));
        }
        return null;
    }

    /** 경매 시 먼저 배당되는 근저당 합계. 토지 등기부를 함께 올렸으면 토지에만 있는 근저당도 더한다 (공동담보는 한 번만). */
    static long seniorMortgage(RiskAssessmentInput input) {
        return input.registry().totalActiveMortgageAmount()
                + LandRegistryComparison.landOnlyActiveMortgageAmount(input.registry(), input.landRegistry());
    }
}
