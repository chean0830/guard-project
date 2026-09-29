package com.projectguard.backend.risk.rules;

import com.projectguard.backend.common.ContractType;
import com.projectguard.backend.registry.RegistryAnalysis;
import com.projectguard.backend.risk.RiskAssessmentInput;
import com.projectguard.backend.risk.RiskSeverity;
import com.projectguard.backend.risk.RiskSignal;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SeniorDebtRatioRuleTest {

    private final SeniorDebtRatioRule rule = new SeniorDebtRatioRule();

    private RegistryAnalysis registryWithActiveMortgage(long amount) {
        return new RegistryAnalysis("주소", "고유번호", List.of(), List.of(), List.of(), amount);
    }

    @Test
    void 선순위채권과_보증금_합이_시세의_80퍼센트_이상이면_HIGH() {
        // 선순위채권 500,000,000 + 보증금 400,000,000 = 900,000,000 / 시세 1,000,000,000 = 90%
        RiskAssessmentInput input = new RiskAssessmentInput(
                registryWithActiveMortgage(500_000_000L), ContractType.JEONSE, 400_000_000L, null, 1_000_000_000L, null, null);

        Optional<RiskSignal> result = rule.evaluate(input);
        assertTrue(result.isPresent());
        assertEquals(RiskSeverity.HIGH, result.get().severity());
    }

    @Test
    void 선순위채권이_없어도_보증금만으로_비율_계산된다() {
        // 선순위채권 0 + 보증금 300,000,000 = 30% -> 신호 없음
        RiskAssessmentInput input = new RiskAssessmentInput(
                registryWithActiveMortgage(0L), ContractType.JEONSE, 300_000_000L, null, 1_000_000_000L, null, null);

        assertTrue(rule.evaluate(input).isEmpty());
    }

    @Test
    void 시세정보가_없으면_평가하지_않는다() {
        RiskAssessmentInput input = new RiskAssessmentInput(
                registryWithActiveMortgage(900_000_000L), ContractType.JEONSE, 400_000_000L, null, null, null, null);

        assertTrue(rule.evaluate(input).isEmpty());
    }
}
