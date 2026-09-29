package com.projectguard.backend.risk;

import com.projectguard.backend.common.ContractType;
import com.projectguard.backend.registry.OwnershipEntry;
import com.projectguard.backend.registry.OwnershipType;
import com.projectguard.backend.registry.RegistryAnalysis;
import com.projectguard.backend.registry.SeizureEntry;
import com.projectguard.backend.registry.SeizureType;
import com.projectguard.backend.risk.rules.JeonseRatioRule;
import com.projectguard.backend.risk.rules.OwnerMismatchRule;
import com.projectguard.backend.risk.rules.SeniorDebtRatioRule;
import com.projectguard.backend.risk.rules.UnresolvedSeizureRule;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class RiskAssessmentServiceTest {

    private final RiskAssessmentService service = new RiskAssessmentService(List.of(
            new UnresolvedSeizureRule(),
            new OwnerMismatchRule(),
            new JeonseRatioRule(),
            new SeniorDebtRatioRule()
    ));

    @Test
    void 위험요소가_많은_매물은_여러_신호와_고위험_판정을_받는다() {
        RegistryAnalysis registry = new RegistryAnalysis(
                "위험 매물 주소", "고유번호",
                List.of(new OwnershipEntry(1, OwnershipType.OWNERSHIP_PRESERVATION, "홍길동", "2020년1월1일", false)),
                List.of(),
                List.of(
                        new SeizureEntry(2, SeizureType.PROVISIONAL_SEIZURE, "2023년1월1일", false),
                        new SeizureEntry(3, SeizureType.AUCTION_COMMENCEMENT, "2024년1월1일", false)
                ),
                900_000_000L // 활성 근저당 합계
        );

        RiskAssessmentInput input = new RiskAssessmentInput(
                registry, ContractType.JEONSE, 200_000_000L, null, 1_000_000_000L, "김철수", null // 계약상대방이 소유자와 다름
        );

        RiskAssessmentResult result = service.assess(input);

        assertTrue(result.hasHighRisk());
        assertTrue(result.signals().size() >= 3,
                "압류 미말소, 소유자 불일치, 선순위채권 비율 신호가 모두 떠야 한다");
    }

    @Test
    void 문제없는_매물은_신호가_없다() {
        RegistryAnalysis registry = new RegistryAnalysis(
                "깨끗한 매물 주소", "고유번호",
                List.of(new OwnershipEntry(1, OwnershipType.OWNERSHIP_PRESERVATION, "홍길동", "2020년1월1일", false)),
                List.of(),
                List.of(),
                0L
        );

        RiskAssessmentInput input = new RiskAssessmentInput(
                registry, ContractType.JEONSE, 300_000_000L, null, 1_000_000_000L, "홍길동", null
        );

        RiskAssessmentResult result = service.assess(input);

        assertTrue(result.signals().isEmpty());
        assertTrue(!result.hasHighRisk());
    }
}
