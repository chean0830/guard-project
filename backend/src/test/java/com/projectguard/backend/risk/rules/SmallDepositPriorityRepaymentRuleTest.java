package com.projectguard.backend.risk.rules;

import com.projectguard.backend.common.ContractType;
import com.projectguard.backend.registry.RegistryAnalysis;
import com.projectguard.backend.risk.RiskAssessmentInput;
import com.projectguard.backend.risk.RiskSeverity;
import com.projectguard.backend.risk.RiskSignal;
import com.projectguard.backend.risk.RiskSource;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SmallDepositPriorityRepaymentRuleTest {

    private final SmallDepositPriorityRepaymentRule rule = new SmallDepositPriorityRepaymentRule();

    private RegistryAnalysis registryWithAddress(String address) {
        return new RegistryAnalysis(address, "고유번호", List.of(), List.of(), List.of(), 0L);
    }

    @Test
    void 서울_소액보증금_기준_이하면_보호대상_안내() {
        RiskAssessmentInput input = new RiskAssessmentInput(
                registryWithAddress("서울특별시 강남구 테스트로 123"), ContractType.JEONSE, 100_000_000L, null, null, null, null);

        Optional<RiskSignal> result = rule.evaluate(input);

        assertTrue(result.isPresent());
        assertEquals(RiskSeverity.INFO, result.get().severity());
        assertEquals(RiskSource.LAW, result.get().source());
        assertTrue(result.get().detail().contains("최우선변제 대상입니다"));
    }

    @Test
    void 서울_소액보증금_기준_초과면_보호대상_아님_안내() {
        RiskAssessmentInput input = new RiskAssessmentInput(
                registryWithAddress("서울특별시 강남구 테스트로 123"), ContractType.JEONSE, 300_000_000L, null, null, null, null);

        Optional<RiskSignal> result = rule.evaluate(input);

        assertTrue(result.isPresent());
        assertTrue(result.get().detail().contains("최우선변제 대상이 아닙니다"));
    }

    @Test
    void 주소가_없으면_평가하지_않는다() {
        RiskAssessmentInput input = new RiskAssessmentInput(
                registryWithAddress(null), ContractType.JEONSE, 100_000_000L, null, null, null, null);

        assertTrue(rule.evaluate(input).isEmpty());
    }
}
