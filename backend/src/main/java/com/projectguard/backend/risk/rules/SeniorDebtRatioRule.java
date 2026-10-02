package com.projectguard.backend.risk.rules;

import com.projectguard.backend.risk.RiskAssessmentInput;
import com.projectguard.backend.risk.RiskRule;
import com.projectguard.backend.risk.RiskSeverity;
import com.projectguard.backend.risk.RiskSignal;
import com.projectguard.backend.risk.RiskSource;
import com.projectguard.backend.risk.RiskThresholds;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * 나보다 먼저 배당받는 선순위 채권(활성 근저당 등)과 내 보증금을 합친 금액이
 * 시세 대비 얼마나 되는지 확인한다. 이 합이 시세에 가까울수록, 집이 경매로 넘어갔을 때
 * 내 보증금을 못 돌려받을 가능성이 커진다. 국토부/금감원이 배포하는 전세사기 예방
 * 안내자료에서 흔히 제시되는 계산식이며, 법적으로 확정된 기준은 아니다.
 */
@Component
public class SeniorDebtRatioRule implements RiskRule {

    @Override
    public Optional<RiskSignal> evaluate(RiskAssessmentInput input) {
        // 다가구주택은 MultiHouseholdPriorDepositRule이 대신 판단한다 (다른 세입자 보증금이 빠진 계산이라 실제보다 안전하게 나온다).
        if (input.isMultiHousehold()) {
            return Optional.empty();
        }

        Long marketPrice = input.marketPrice();
        if (marketPrice == null || marketPrice <= 0) {
            return Optional.empty();
        }

        long seniorDebt = input.registry().totalActiveMortgageAmount();
        long combined = seniorDebt + input.depositAmount();
        double ratio = (double) combined / marketPrice;

        RiskSeverity severity;
        if (ratio >= RiskThresholds.SENIOR_DEBT_PLUS_DEPOSIT_RATIO_HIGH) {
            severity = RiskSeverity.HIGH;
        } else if (ratio >= RiskThresholds.SENIOR_DEBT_PLUS_DEPOSIT_RATIO_CAUTION) {
            severity = RiskSeverity.CAUTION;
        } else {
            return Optional.empty();
        }

        String detail = String.format(
                "선순위 채권(활성 근저당 등) %,d원과 보증금을 더하면 시세의 %.1f%%입니다. "
                        + "이 비율이 높을수록 경매로 넘어갔을 때 보증금을 다 돌려받지 못할 위험이 커집니다.",
                seniorDebt, ratio * 100);

        return Optional.of(new RiskSignal(
                "HIGH_SENIOR_DEBT_RATIO",
                "선순위 채권과 보증금 합계가 시세 대비 높음",
                severity,
                RiskSource.GOVERNMENT_GUIDELINE,
                "국토교통부/금융감독원 전세사기 예방 안내자료 참고 (법적 기준 아님, 참고 지표)",
                detail
        ));
    }
}
