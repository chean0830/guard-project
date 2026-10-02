package com.projectguard.backend.risk.rules;

import com.projectguard.backend.market.OfficialHousePrice;
import com.projectguard.backend.common.ContractType;
import com.projectguard.backend.common.PriorDepositSource;
import com.projectguard.backend.common.PropertyType;
import com.projectguard.backend.registry.RegistryAnalysis;
import com.projectguard.backend.risk.RiskAssessmentInput;
import com.projectguard.backend.risk.RiskSeverity;
import com.projectguard.backend.risk.RiskSignal;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MultiHouseholdPriorDepositRuleTest {

    private final MultiHouseholdPriorDepositRule rule = new MultiHouseholdPriorDepositRule();

    private RiskAssessmentInput multiHousehold(
            long mortgage, long deposit, Long buildingPrice, Long priorDeposit, PriorDepositSource source
    ) {
        RegistryAnalysis registry = new RegistryAnalysis("주소", "고유번호", List.of(), List.of(), List.of(), mortgage);
        return new RiskAssessmentInput(registry, ContractType.JEONSE, deposit, null, buildingPrice, null, null,
                PropertyType.MULTI_HOUSEHOLD, priorDeposit, source);
    }

    @Test
    void 다가구가_아니면_평가하지_않는다() {
        RegistryAnalysis registry = new RegistryAnalysis("주소", "고유번호", List.of(), List.of(), List.of(), 0L);
        RiskAssessmentInput input = new RiskAssessmentInput(
                registry, ContractType.JEONSE, 100_000_000L, null, 1_000_000_000L, null, null);

        assertTrue(rule.evaluate(input).isEmpty());
    }

    @Test
    void 임대인_말로만_확인했으면_비율이_낮아도_HIGH() {
        // 근저당 0 + 선순위 0 + 보증금 1억 = 10% 지만 출처가 임대인 말이라 믿을 수 없음
        Optional<RiskSignal> result = rule.evaluate(
                multiHousehold(0L, 100_000_000L, 1_000_000_000L, 0L, PriorDepositSource.LANDLORD_CLAIM));

        assertTrue(result.isPresent());
        assertEquals(RiskSeverity.HIGH, result.get().severity());
        assertEquals("MULTI_HOUSEHOLD_PRIOR_DEPOSIT_UNVERIFIED", result.get().code());
        assertTrue(result.get().detail().contains("믿으면 안 됩니다"));
    }

    @Test
    void 선순위_보증금을_모르면_HIGH() {
        Optional<RiskSignal> result = rule.evaluate(
                multiHousehold(0L, 100_000_000L, 1_000_000_000L, null, PriorDepositSource.UNKNOWN));

        assertEquals(RiskSeverity.HIGH, result.orElseThrow().severity());
    }

    @Test
    void 출처를_서류로_골랐어도_금액이_없으면_HIGH() {
        Optional<RiskSignal> result = rule.evaluate(
                multiHousehold(0L, 100_000_000L, 1_000_000_000L, null, PriorDepositSource.OFFICIAL_DOCUMENT));

        assertEquals("MULTI_HOUSEHOLD_PRIOR_DEPOSIT_UNVERIFIED", result.orElseThrow().code());
    }

    @Test
    void 서류로_확인했지만_건물_시세가_없으면_CAUTION() {
        Optional<RiskSignal> result = rule.evaluate(
                multiHousehold(0L, 100_000_000L, null, 300_000_000L, PriorDepositSource.OFFICIAL_DOCUMENT));

        assertEquals(RiskSeverity.CAUTION, result.orElseThrow().severity());
        assertEquals("MULTI_HOUSEHOLD_PRICE_UNKNOWN", result.get().code());
    }

    @Test
    void 근저당_선순위보증금_내보증금_합이_시세의_80퍼센트_이상이면_HIGH() {
        // 근저당 3억 + 선순위 4억 + 보증금 1억 = 8억 / 10억 = 80%
        Optional<RiskSignal> result = rule.evaluate(multiHousehold(
                300_000_000L, 100_000_000L, 1_000_000_000L, 400_000_000L, PriorDepositSource.OFFICIAL_DOCUMENT));

        assertEquals(RiskSeverity.HIGH, result.orElseThrow().severity());
        assertTrue(result.get().detail().contains("80.0%"));
    }

    @Test
    void 비율이_낮아도_안전_판정_대신_조건부_CAUTION() {
        // 0 + 1억 + 1억 = 2억 / 10억 = 20%
        Optional<RiskSignal> result = rule.evaluate(multiHousehold(
                0L, 100_000_000L, 1_000_000_000L, 100_000_000L, PriorDepositSource.OFFICIAL_DOCUMENT));

        assertTrue(result.isPresent());
        assertEquals(RiskSeverity.CAUTION, result.get().severity());
        assertTrue(result.get().title().contains("조건부"));
    }

    @Test
    void 건물_시세를_입력하지_않으면_공시가격으로_계산한다() {
        // 근저당 0 + 선순위 4억 + 보증금 1억 = 5억 / 공시가격 6억 = 83.3% -> HIGH
        RegistryAnalysis registry = new RegistryAnalysis("주소", "고유번호", List.of(), List.of(), List.of(), 0L);
        RiskAssessmentInput input = new RiskAssessmentInput(registry, ContractType.JEONSE, 100_000_000L, null, null,
                null, null, PropertyType.MULTI_HOUSEHOLD, 400_000_000L, PriorDepositSource.OFFICIAL_DOCUMENT,
                null, null, new OfficialHousePrice(600_000_000L, 2026));

        RiskSignal signal = rule.evaluate(input).orElseThrow();

        assertEquals(RiskSeverity.HIGH, signal.severity());
        assertTrue(signal.detail().contains("공시가격 600,000,000원(2026년 기준"));
    }

    @Test
    void 입력한_건물_시세가_있으면_공시가격보다_우선한다() {
        RegistryAnalysis registry = new RegistryAnalysis("주소", "고유번호", List.of(), List.of(), List.of(), 0L);
        RiskAssessmentInput input = new RiskAssessmentInput(registry, ContractType.JEONSE, 100_000_000L, null,
                2_000_000_000L, null, null, PropertyType.MULTI_HOUSEHOLD, 400_000_000L,
                PriorDepositSource.OFFICIAL_DOCUMENT, null, null, new OfficialHousePrice(600_000_000L, 2026));

        RiskSignal signal = rule.evaluate(input).orElseThrow();

        assertEquals(RiskSeverity.CAUTION, signal.severity());
        assertTrue(signal.detail().contains("건물 시세 2,000,000,000원(입력값)"));
    }
}
