package com.projectguard.backend.risk.rules;

import com.projectguard.backend.common.ContractType;
import com.projectguard.backend.registry.RegistryAnalysis;
import com.projectguard.backend.risk.RiskAssessmentInput;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class AddressMismatchRuleTest {

    private final AddressMismatchRule rule = new AddressMismatchRule();

    private RegistryAnalysis registryWithAddress(String address) {
        return new RegistryAnalysis(address, "고유번호", List.of(), List.of(), List.of(), 0L);
    }

    private RiskAssessmentInput inputWith(String registryAddress, String declaredAddress) {
        return new RiskAssessmentInput(
                registryWithAddress(registryAddress), ContractType.JEONSE, 100_000_000L, null, null, null, declaredAddress);
    }

    @Test
    void 주소가_완전히_다르면_신호를_반환한다() {
        RiskAssessmentInput input = inputWith("서울특별시 강남구 테스트로 123 101동 501호", "서울특별시 서초구 다른로 456");
        assertTrue(rule.evaluate(input).isPresent());
    }

    @Test
    void 공백_차이만_있으면_신호가_없다() {
        RiskAssessmentInput input = inputWith("서울특별시 강남구 테스트로 123 101동 501호", "서울특별시 강남구 테스트로 123   101동 501호");
        assertTrue(rule.evaluate(input).isEmpty());
    }

    @Test
    void 한쪽이_다른쪽의_부분집합이면_신호가_없다() {
        // 사용자가 상세 동호수까지 안 적고 도로명주소만 입력한 경우
        RiskAssessmentInput input = inputWith("서울특별시 강남구 테스트로 123 101동 501호", "서울특별시 강남구 테스트로 123");
        assertTrue(rule.evaluate(input).isEmpty());
    }

    @Test
    void 계약주소를_입력하지_않으면_신호가_없다() {
        RiskAssessmentInput input = inputWith("서울특별시 강남구 테스트로 123", null);
        assertTrue(rule.evaluate(input).isEmpty());
    }
}
