package com.projectguard.backend.risk.rules;

import com.projectguard.backend.risk.RiskAssessmentInput;
import com.projectguard.backend.risk.RiskRule;
import com.projectguard.backend.risk.RiskSeverity;
import com.projectguard.backend.risk.RiskSignal;
import com.projectguard.backend.risk.RiskSource;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * 등기부상 주소와, 사용자가 입력한 계약서상 주소가 다른지 확인한다. 동·호수 표기 차이 같은
 * 사소한 표기 차이는 흔하므로, 공백을 지우고 한쪽이 다른 쪽을 포함하는지로 느슨하게 비교한다
 * (MarketPriceMatcher의 단지명 비교와 같은 방식). 문자열 비교의 한계가 있어 소유자 불일치
 * (OwnerMismatchRule, FACTUAL/HIGH)보다 낮은 CAUTION으로 표시한다.
 */
@Component
public class AddressMismatchRule implements RiskRule {

    @Override
    public Optional<RiskSignal> evaluate(RiskAssessmentInput input) {
        String declaredAddress = input.declaredAddress();
        if (declaredAddress == null || declaredAddress.isBlank()) {
            return Optional.empty();
        }

        String registryAddress = input.registry().address();
        if (registryAddress == null || registryAddress.isBlank()) {
            return Optional.empty();
        }

        String normalizedDeclared = normalize(declaredAddress);
        String normalizedRegistry = normalize(registryAddress);

        if (normalizedRegistry.contains(normalizedDeclared) || normalizedDeclared.contains(normalizedRegistry)) {
            return Optional.empty();
        }

        String detail = String.format(
                "등기부상 주소는 '%s'인데, 입력하신 계약서상 주소는 '%s'입니다. "
                        + "동·호수 표기 차이일 수도 있지만, 다른 집을 착각한 것은 아닌지 다시 확인해보세요.",
                registryAddress, declaredAddress);

        return Optional.of(new RiskSignal(
                "ADDRESS_MISMATCH",
                "등기부상 주소와 계약서상 주소가 다름",
                RiskSeverity.CAUTION,
                RiskSource.FACTUAL,
                "등기사항증명서 표제부 주소와 입력값 대조 (문자열 비교라 표기 차이는 오탐일 수 있음)",
                detail
        ));
    }

    private String normalize(String address) {
        return address.replaceAll("\\s+", "");
    }
}
