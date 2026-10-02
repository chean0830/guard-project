package com.projectguard.backend.risk.rules;

import com.projectguard.backend.common.ContractType;
import com.projectguard.backend.common.PropertyType;
import com.projectguard.backend.registry.RegistryAnalysis;
import com.projectguard.backend.risk.RiskAssessmentInput;
import com.projectguard.backend.risk.RiskSeverity;
import com.projectguard.backend.risk.RiskSignal;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JeonseRatioRuleTest {

    private final JeonseRatioRule rule = new JeonseRatioRule();

    private RegistryAnalysis emptyRegistry() {
        return new RegistryAnalysis("주소", "고유번호", List.of(), List.of(), List.of(), 0L);
    }

    @Test
    void 시세정보가_없으면_평가하지_않는다() {
        RiskAssessmentInput input = new RiskAssessmentInput(
                emptyRegistry(), ContractType.JEONSE, 900_000_000L, null, null, null, null);
        assertTrue(rule.evaluate(input).isEmpty());
    }

    @Test
    void 전세가율_90퍼센트_이상이면_HIGH() {
        RiskAssessmentInput input = new RiskAssessmentInput(
                emptyRegistry(), ContractType.JEONSE, 950_000_000L, null, 1_000_000_000L, null, null);
        Optional<RiskSignal> result = rule.evaluate(input);
        assertTrue(result.isPresent());
        assertEquals(RiskSeverity.HIGH, result.get().severity());
    }

    @Test
    void 전세가율_80에서_90퍼센트면_CAUTION() {
        RiskAssessmentInput input = new RiskAssessmentInput(
                emptyRegistry(), ContractType.JEONSE, 850_000_000L, null, 1_000_000_000L, null, null);
        Optional<RiskSignal> result = rule.evaluate(input);
        assertTrue(result.isPresent());
        assertEquals(RiskSeverity.CAUTION, result.get().severity());
    }

    @Test
    void 전세가율_80퍼센트_미만이면_신호없음() {
        RiskAssessmentInput input = new RiskAssessmentInput(
                emptyRegistry(), ContractType.JEONSE, 500_000_000L, null, 1_000_000_000L, null, null);
        assertTrue(rule.evaluate(input).isEmpty());
    }

    @Test
    void 월세는_법정_전환율로_환산해서_비교한다() {
        // 보증금 1억 + 월세 300만원(연 3600만 / 5% = 7억2천 환산) = 8억2천 / 시세 10억 = 82% -> CAUTION
        RiskAssessmentInput input = new RiskAssessmentInput(
                emptyRegistry(), ContractType.WOLSE, 100_000_000L, 3_000_000L, 1_000_000_000L, null, null);
        Optional<RiskSignal> result = rule.evaluate(input);
        assertTrue(result.isPresent());
        assertEquals(RiskSeverity.CAUTION, result.get().severity());
        assertEquals("환산 전세가율이 높음", result.get().title());
    }

    @Test
    void 월세인데_월세금액이_없으면_평가하지_않는다() {
        RiskAssessmentInput input = new RiskAssessmentInput(
                emptyRegistry(), ContractType.WOLSE, 100_000_000L, null, 1_000_000_000L, null, null);
        assertTrue(rule.evaluate(input).isEmpty());
    }

    @Test
    void 다가구주택은_전용_규칙이_판단하므로_평가하지_않는다() {
        RegistryAnalysis registry = new RegistryAnalysis("주소", "고유번호", List.of(), List.of(), List.of(), 900_000_000L);
        RiskAssessmentInput input = new RiskAssessmentInput(
                registry, ContractType.JEONSE, 400_000_000L, null, 1_000_000_000L, null, null,
                PropertyType.MULTI_HOUSEHOLD, null, null);

        assertTrue(rule.evaluate(input).isEmpty());
    }
}
