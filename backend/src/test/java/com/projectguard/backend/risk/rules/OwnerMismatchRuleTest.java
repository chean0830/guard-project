package com.projectguard.backend.risk.rules;

import com.projectguard.backend.common.ContractType;
import com.projectguard.backend.registry.OwnershipEntry;
import com.projectguard.backend.registry.OwnershipType;
import com.projectguard.backend.registry.RegistryAnalysis;
import com.projectguard.backend.risk.RiskAssessmentInput;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class OwnerMismatchRuleTest {

    private final OwnerMismatchRule rule = new OwnerMismatchRule();

    private RegistryAnalysis registryWithOwner(String ownerName) {
        return new RegistryAnalysis(
                "테스트 주소", "테스트 고유번호",
                List.of(new OwnershipEntry(1, OwnershipType.OWNERSHIP_PRESERVATION, ownerName, "2020년1월1일", false)),
                List.of(), List.of(), 0L
        );
    }

    @Test
    void 소유자와_계약상대방이_다르면_신호를_반환한다() {
        RiskAssessmentInput input = new RiskAssessmentInput(
                registryWithOwner("홍길동"), ContractType.JEONSE, 100_000_000L, null, null, "김철수", null);

        assertTrue(rule.evaluate(input).isPresent());
    }

    @Test
    void 소유자와_계약상대방이_같으면_신호가_없다() {
        RiskAssessmentInput input = new RiskAssessmentInput(
                registryWithOwner("홍길동"), ContractType.JEONSE, 100_000_000L, null, null, "홍길동", null);

        assertTrue(rule.evaluate(input).isEmpty());
    }

    @Test
    void 계약상대방을_입력하지_않으면_신호가_없다() {
        RiskAssessmentInput input = new RiskAssessmentInput(
                registryWithOwner("홍길동"), ContractType.JEONSE, 100_000_000L, null, null, null, null);

        assertTrue(rule.evaluate(input).isEmpty());
    }
}
