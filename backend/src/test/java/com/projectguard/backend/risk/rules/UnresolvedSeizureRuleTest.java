package com.projectguard.backend.risk.rules;

import com.projectguard.backend.common.ContractType;
import com.projectguard.backend.registry.RegistryAnalysis;
import com.projectguard.backend.registry.SeizureEntry;
import com.projectguard.backend.registry.SeizureType;
import com.projectguard.backend.risk.RiskAssessmentInput;
import com.projectguard.backend.risk.RiskSeverity;
import com.projectguard.backend.risk.RiskSignal;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UnresolvedSeizureRuleTest {

    private final UnresolvedSeizureRule rule = new UnresolvedSeizureRule();

    @Test
    void 말소되지_않은_압류가_있으면_HIGH_신호를_반환한다() {
        RegistryAnalysis registry = new RegistryAnalysis(
                "테스트 주소", "테스트 고유번호",
                List.of(), List.of(),
                List.of(new SeizureEntry(1, SeizureType.PROVISIONAL_SEIZURE, "2024년1월1일", false)),
                0L
        );
        RiskAssessmentInput input = new RiskAssessmentInput(registry, ContractType.JEONSE, 100_000_000L, null, null, null, null);

        Optional<RiskSignal> result = rule.evaluate(input);

        assertTrue(result.isPresent());
        assertEquals(RiskSeverity.HIGH, result.get().severity());
    }

    @Test
    void 압류가_모두_말소됐으면_신호가_없다() {
        RegistryAnalysis registry = new RegistryAnalysis(
                "테스트 주소", "테스트 고유번호",
                List.of(), List.of(),
                List.of(new SeizureEntry(1, SeizureType.PROVISIONAL_SEIZURE, "2024년1월1일", true)),
                0L
        );
        RiskAssessmentInput input = new RiskAssessmentInput(registry, ContractType.JEONSE, 100_000_000L, null, null, null, null);

        assertTrue(rule.evaluate(input).isEmpty());
    }
}
