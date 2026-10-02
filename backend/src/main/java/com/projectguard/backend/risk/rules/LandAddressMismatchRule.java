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
 * 함께 올린 토지 등기부가 건물과 같은 필지(지번)인지 확인한다. 다른 필지 등기부를 올렸다면 토지 분석 결과가
 * 이 건물과 무관하다. 건물이 여러 필지에 걸쳐 있으면 정상인데도 다르게 나올 수 있어 CAUTION으로만 알린다.
 */
@Component
public class LandAddressMismatchRule implements RiskRule {

    @Override
    public Optional<RiskSignal> evaluate(RiskAssessmentInput input) {
        RegistryAnalysis land = input.landRegistry();
        if (land == null || land.address() == null || input.registry().address() == null) {
            return Optional.empty();
        }
        String buildingAddress = normalize(input.registry().address());
        String landAddress = normalize(land.address());
        if (buildingAddress.contains(landAddress) || landAddress.contains(buildingAddress)) {
            return Optional.empty();
        }

        return Optional.of(new RiskSignal(
                "LAND_ADDRESS_MISMATCH",
                "토지 등기부 주소가 건물과 달라요",
                RiskSeverity.CAUTION,
                RiskSource.FACTUAL,
                "건물·토지 등기사항증명서 주소 대조",
                String.format("건물 등기부 주소는 '%s', 토지 등기부 주소는 '%s'예요. 다른 필지의 토지 등기부라면 "
                                + "토지 분석 결과가 이 건물과 무관하니, 건물이 있는 지번의 토지 등기부를 다시 확인하세요. "
                                + "건물이 여러 필지에 걸쳐 있다면 각 필지의 토지 등기부를 모두 확인해야 해요.",
                        input.registry().address(), land.address())));
    }

    private String normalize(String address) {
        return address.replaceAll("\\s+", "");
    }
}
