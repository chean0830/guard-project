package com.projectguard.backend.risk.rules;

import com.projectguard.backend.registry.RegistryAnalysis;
import com.projectguard.backend.risk.RiskAssessmentInput;
import com.projectguard.backend.risk.RiskRule;
import com.projectguard.backend.risk.RiskSeverity;
import com.projectguard.backend.risk.RiskSignal;
import com.projectguard.backend.risk.RiskSource;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * 토지와 건물 소유자가 다르면, 토지 소유자가 건물 철거를 요구하거나 토지만 경매로 넘어가는 등
 * 권리관계가 복잡해져 보증금 회수가 어려워질 수 있다. 토지 등기부를 함께 올렸을 때만 확인한다.
 */
@Component
public class LandOwnerMismatchRule implements RiskRule {

    @Override
    public Optional<RiskSignal> evaluate(RiskAssessmentInput input) {
        RegistryAnalysis land = input.landRegistry();
        if (land == null) {
            return Optional.empty();
        }
        String buildingOwner = input.registry().currentOwnerName();
        String landOwner = land.currentOwnerName();
        if (buildingOwner == null || landOwner == null || buildingOwner.equals(landOwner)) {
            return Optional.empty();
        }

        return Optional.of(new RiskSignal(
                "LAND_OWNER_MISMATCH",
                "토지와 건물 소유자가 달라요",
                RiskSeverity.HIGH,
                RiskSource.FACTUAL,
                "건물·토지 등기사항증명서 갑구 소유자 대조",
                String.format("건물 소유자는 '%s', 토지 소유자는 '%s'예요. 토지와 건물 주인이 다르면 토지 소유자가 "
                                + "건물 철거나 토지 사용료를 요구하거나 토지만 따로 경매로 넘어갈 수 있어, 보증금을 돌려받기 "
                                + "어려워질 수 있어요. 계약 전에 두 사람의 관계와 토지 사용 권리(지상권 등)를 꼭 확인하세요.",
                        buildingOwner, landOwner)));
    }
}
