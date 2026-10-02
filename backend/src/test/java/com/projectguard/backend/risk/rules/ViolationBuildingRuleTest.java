package com.projectguard.backend.risk.rules;

import com.projectguard.backend.common.ContractType;
import com.projectguard.backend.common.PropertyType;
import com.projectguard.backend.common.ViolationBuildingAnswer;
import com.projectguard.backend.registry.RegistryAnalysis;
import com.projectguard.backend.risk.RiskAssessmentInput;
import com.projectguard.backend.risk.RiskSeverity;
import com.projectguard.backend.risk.RiskSignal;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ViolationBuildingRuleTest {

    private final ViolationBuildingRule rule = new ViolationBuildingRule();

    private Optional<RiskSignal> evaluate(ContractType contract, ViolationBuildingAnswer answer) {
        RegistryAnalysis registry = new RegistryAnalysis("주소", "고유번호", List.of(), List.of(), List.of(), 0L);
        return rule.evaluate(new RiskAssessmentInput(
                registry, contract, 100_000_000L, contract == ContractType.WOLSE ? 500_000L : null, null, null, null,
                PropertyType.VILLA, null, null, null, answer));
    }

    @Test
    void 위반건축물_표시가_있고_전세면_HIGH() {
        assertEquals(RiskSeverity.HIGH, evaluate(ContractType.JEONSE, ViolationBuildingAnswer.MARKED).orElseThrow().severity());
    }

    @Test
    void 위반건축물_표시가_있고_월세면_CAUTION() {
        assertEquals(RiskSeverity.CAUTION, evaluate(ContractType.WOLSE, ViolationBuildingAnswer.MARKED).orElseThrow().severity());
    }

    @Test
    void 표시가_없거나_모르거나_입력하지_않았으면_신호가_없다() {
        assertTrue(evaluate(ContractType.JEONSE, ViolationBuildingAnswer.NOT_MARKED).isEmpty());
        assertTrue(evaluate(ContractType.JEONSE, ViolationBuildingAnswer.UNKNOWN).isEmpty());
        assertTrue(evaluate(ContractType.JEONSE, null).isEmpty());
    }
}
