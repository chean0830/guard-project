package com.projectguard.backend.risk.rules;

import com.projectguard.backend.registry.RegistryAnalysis;
import com.projectguard.backend.registry.SeizureEntry;
import com.projectguard.backend.risk.RiskAssessmentInput;
import com.projectguard.backend.risk.RiskRule;
import com.projectguard.backend.risk.RiskSeverity;
import com.projectguard.backend.risk.RiskSignal;
import com.projectguard.backend.risk.RiskSource;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * 토지 등기부에 말소되지 않은 압류·가압류·경매개시결정·가처분이 있는지 확인한다.
 * 건물 등기부가 깨끗해도 토지가 경매로 넘어가면 건물 임차인도 영향을 받는다 (UnresolvedSeizureRule의 토지판).
 */
@Component
public class LandSeizureRule implements RiskRule {

    @Override
    public Optional<RiskSignal> evaluate(RiskAssessmentInput input) {
        RegistryAnalysis land = input.landRegistry();
        if (land == null) {
            return Optional.empty();
        }
        List<SeizureEntry> unresolved = land.seizures().stream().filter(s -> !s.cancelled()).toList();
        if (unresolved.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(new RiskSignal(
                "LAND_UNRESOLVED_SEIZURE",
                "토지에 말소되지 않은 압류/가압류 기록이 있어요",
                RiskSeverity.HIGH,
                RiskSource.FACTUAL,
                "토지 등기사항증명서 갑구에 기록된 사실",
                String.format("토지 등기부에 말소되지 않은 압류/가압류/경매 관련 기록이 %d건 있어요. 건물 등기부가 깨끗해도 "
                        + "토지가 경매로 넘어가면 보증금을 돌려받기 어려워질 수 있어요.", unresolved.size())));
    }
}
