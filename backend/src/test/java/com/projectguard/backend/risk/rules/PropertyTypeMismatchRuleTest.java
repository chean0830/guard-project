package com.projectguard.backend.risk.rules;

import com.projectguard.backend.common.ContractType;
import com.projectguard.backend.common.PropertyType;
import com.projectguard.backend.registry.RegistryAnalysis;
import com.projectguard.backend.registry.RegistryKind;
import com.projectguard.backend.risk.RiskAssessmentInput;
import com.projectguard.backend.risk.RiskSeverity;
import com.projectguard.backend.risk.RiskSignal;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PropertyTypeMismatchRuleTest {

    private final PropertyTypeMismatchRule rule = new PropertyTypeMismatchRule();

    private Optional<RiskSignal> evaluate(PropertyType propertyType, RegistryKind kind) {
        RegistryAnalysis registry = new RegistryAnalysis(
                "주소", "고유번호", List.of(), List.of(), List.of(), 0L, kind);
        return rule.evaluate(new RiskAssessmentInput(
                registry, ContractType.JEONSE, 100_000_000L, null, null, null, null, propertyType, null, null));
    }

    @Test
    void 유형과_등기부_종류가_맞으면_신호가_없다() {
        assertTrue(evaluate(PropertyType.APARTMENT, RegistryKind.COLLECTIVE_BUILDING).isEmpty());
        assertTrue(evaluate(PropertyType.VILLA, RegistryKind.COLLECTIVE_BUILDING).isEmpty());
        assertTrue(evaluate(PropertyType.MULTI_HOUSEHOLD, RegistryKind.BUILDING).isEmpty());
    }

    @Test
    void 빌라를_골랐는데_건물_등기부면_HIGH() {
        Optional<RiskSignal> result = evaluate(PropertyType.VILLA, RegistryKind.BUILDING);

        assertEquals(RiskSeverity.HIGH, result.orElseThrow().severity());
        assertTrue(result.get().detail().contains("원룸·다가구주택"));
    }

    @Test
    void 다가구를_골랐는데_집합건물_등기부면_HIGH() {
        Optional<RiskSignal> result = evaluate(PropertyType.MULTI_HOUSEHOLD, RegistryKind.COLLECTIVE_BUILDING);

        assertEquals(RiskSeverity.HIGH, result.orElseThrow().severity());
        assertTrue(result.get().title().contains("집합건물"));
    }

    @Test
    void 토지_등기부면_유형과_상관없이_HIGH() {
        assertEquals(RiskSeverity.HIGH, evaluate(PropertyType.APARTMENT, RegistryKind.LAND).orElseThrow().severity());
        assertEquals(RiskSeverity.HIGH,
                evaluate(PropertyType.MULTI_HOUSEHOLD, RegistryKind.LAND).orElseThrow().severity());
    }

    @Test
    void 등기부_종류를_모르면_판단하지_않는다() {
        assertTrue(evaluate(PropertyType.APARTMENT, RegistryKind.UNKNOWN).isEmpty());
        assertTrue(evaluate(PropertyType.MULTI_HOUSEHOLD, RegistryKind.UNKNOWN).isEmpty());
    }
}
