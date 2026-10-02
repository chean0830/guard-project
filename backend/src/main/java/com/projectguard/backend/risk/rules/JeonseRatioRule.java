package com.projectguard.backend.risk.rules;

import com.projectguard.backend.common.ContractType;
import com.projectguard.backend.risk.RiskAssessmentInput;
import com.projectguard.backend.risk.RiskRule;
import com.projectguard.backend.risk.RiskSeverity;
import com.projectguard.backend.risk.RiskSignal;
import com.projectguard.backend.risk.RiskSource;
import com.projectguard.backend.risk.RiskThresholds;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * 전세가율(보증금/시세)이 높을수록 집값이 떨어지면 보증금을 못 돌려받을 위험(깡통전세)이 커진다.
 * HUG 전세보증금 반환보증 심사에서 쓰이는 기준을 참고한 권고 지표이며, 법적 기준은 아니다.
 * 월세는 보증금만으로 비교하면 항상 낮게 나와 의미가 없으므로, 월세를 법정 상한 전환율로
 * 보증금에 환산한 "환산보증금"을 대신 사용한다 (docs/결정사항.md 참고).
 * 시세(marketPrice)가 아직 조회되지 않았으면 평가하지 않는다.
 */
@Component
public class JeonseRatioRule implements RiskRule {

    @Override
    public Optional<RiskSignal> evaluate(RiskAssessmentInput input) {
        // 다가구주택은 MultiHouseholdPriorDepositRule이 대신 판단한다 (내 보증금만 건물 전체 시세와 비교하면 항상 낮게 나와 의미가 없다).
        if (input.isMultiHousehold()) {
            return Optional.empty();
        }

        Long marketPrice = input.marketPrice();
        if (marketPrice == null || marketPrice <= 0) {
            return Optional.empty();
        }

        boolean isWolse = input.contractType() == ContractType.WOLSE;
        if (isWolse && (input.monthlyRent() == null || input.monthlyRent() <= 0)) {
            return Optional.empty();
        }

        long comparisonDeposit = isWolse
                ? input.depositAmount() + convertMonthlyRentToDeposit(input.monthlyRent())
                : input.depositAmount();

        double ratio = (double) comparisonDeposit / marketPrice;

        RiskSeverity severity;
        if (ratio >= RiskThresholds.JEONSE_RATIO_HIGH) {
            severity = RiskSeverity.HIGH;
        } else if (ratio >= RiskThresholds.JEONSE_RATIO_CAUTION) {
            severity = RiskSeverity.CAUTION;
        } else {
            return Optional.empty();
        }

        String title = isWolse ? "환산 전세가율이 높음" : "전세가율이 높음";
        String detail = isWolse
                ? String.format(
                        "보증금에 월세를 법정 전환율(연 %.1f%%)로 환산해 더하면 시세의 %.1f%% 수준입니다. "
                                + "비율이 높을수록 집값이 떨어졌을 때 보증금을 전액 돌려받지 못할 위험이 커집니다.",
                        RiskThresholds.MONTHLY_RENT_CONVERSION_RATE * 100, ratio * 100)
                : String.format(
                        "보증금이 시세의 %.1f%% 수준입니다. 전세가율이 높을수록 집값이 떨어졌을 때 "
                                + "보증금을 전액 돌려받지 못할 위험이 커집니다.",
                        ratio * 100);
        String sourceDescription = isWolse
                ? "HUG 전세보증금 반환보증 심사 기준 참고 (법적 기준 아님, 참고 지표). "
                        + "월세→보증금 환산율은 주택임대차보호법 시행령 제9조 상한(기준금리+2%p, 10% 중 낮은 값) 적용"
                : "HUG 전세보증금 반환보증 심사 기준 참고 (법적 기준 아님, 참고 지표)";

        return Optional.of(new RiskSignal(
                "HIGH_JEONSE_RATIO",
                title,
                severity,
                RiskSource.GOVERNMENT_GUIDELINE,
                sourceDescription,
                detail
        ));
    }

    private long convertMonthlyRentToDeposit(long monthlyRent) {
        return Math.round((monthlyRent * 12) / RiskThresholds.MONTHLY_RENT_CONVERSION_RATE);
    }
}
