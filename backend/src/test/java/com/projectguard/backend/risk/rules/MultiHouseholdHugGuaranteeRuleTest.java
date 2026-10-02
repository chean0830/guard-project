package com.projectguard.backend.risk.rules;

import com.projectguard.backend.common.ContractType;
import com.projectguard.backend.common.PriorDepositSource;
import com.projectguard.backend.common.PropertyType;
import com.projectguard.backend.common.ViolationBuildingAnswer;
import com.projectguard.backend.market.OfficialHousePrice;
import com.projectguard.backend.registry.RegistryAnalysis;
import com.projectguard.backend.registry.SeizureEntry;
import com.projectguard.backend.registry.SeizureType;
import com.projectguard.backend.risk.RiskAssessmentInput;
import com.projectguard.backend.risk.RiskSeverity;
import com.projectguard.backend.risk.RiskSignal;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 공시가격 10억 → HUG 주택가격 14억 → 60% 8억4천, 80% 11억2천, 90% 12억6천. */
class MultiHouseholdHugGuaranteeRuleTest {

    private final MultiHouseholdHugGuaranteeRule rule = new MultiHouseholdHugGuaranteeRule();
    private static final OfficialHousePrice OFFICIAL = new OfficialHousePrice(1_000_000_000L, 2026);

    private RiskSignal evaluate(String address, long mortgage, long prior, PriorDepositSource source, long deposit,
                                OfficialHousePrice official, ViolationBuildingAnswer violation, List<SeizureEntry> seizures) {
        RegistryAnalysis registry = new RegistryAnalysis(address, "고유번호", List.of(), List.of(), seizures, mortgage);
        return rule.evaluate(new RiskAssessmentInput(registry, ContractType.JEONSE, deposit, null, null, null, null,
                PropertyType.MULTI_HOUSEHOLD, prior, source, null, violation, official, null, null)).orElseThrow();
    }

    private RiskSignal evaluate(long mortgage, long prior, long deposit) {
        return evaluate("서울특별시 관악구 봉천동 1", mortgage, prior, PriorDepositSource.OFFICIAL_DOCUMENT, deposit,
                OFFICIAL, null, List.of());
    }

    @Test
    void 기준_안이면_INFO로_가입_가능성을_알린다() {
        // 3억 + 5억 + 2억 = 10억 ≤ 12억6천
        RiskSignal signal = evaluate(300_000_000L, 500_000_000L, 200_000_000L);

        assertEquals(RiskSeverity.INFO, signal.severity());
        assertTrue(signal.detail().contains("1,400,000,000원"));
    }

    @Test
    void 합계가_주택가격의_90퍼센트를_넘으면_CAUTION() {
        // 3억 + 7억 + 3억 = 13억 > 12억6천 (80% 조건 10억 ≤ 11억2천은 통과)
        RiskSignal signal = evaluate(300_000_000L, 700_000_000L, 300_000_000L);

        assertEquals(RiskSeverity.CAUTION, signal.severity());
        assertTrue(signal.detail().contains("90%(1,260,000,000원)"));
    }

    @Test
    void 근저당이_60퍼센트를_넘으면_CAUTION() {
        RiskSignal signal = evaluate(900_000_000L, 0L, 100_000_000L);

        assertTrue(signal.detail().contains("60%(840,000,000원)"));
        assertEquals(RiskSeverity.CAUTION, signal.severity());
    }

    @Test
    void 근저당과_다른_세입자_보증금이_80퍼센트를_넘으면_CAUTION() {
        RiskSignal signal = evaluate(500_000_000L, 700_000_000L, 0L);

        assertTrue(signal.detail().contains("80%(1,120,000,000원)"));
    }

    @Test
    void 위반건축물이나_압류가_있으면_금액과_상관없이_CAUTION() {
        RiskSignal violation = evaluate("서울특별시 관악구 봉천동 1", 0L, 0L, PriorDepositSource.OFFICIAL_DOCUMENT,
                100_000_000L, OFFICIAL, ViolationBuildingAnswer.MARKED, List.of());
        RiskSignal seizure = evaluate("서울특별시 관악구 봉천동 1", 0L, 0L, PriorDepositSource.OFFICIAL_DOCUMENT,
                100_000_000L, OFFICIAL, null, List.of(new SeizureEntry(2, SeizureType.SEIZURE, "2025년1월1일", false)));

        assertEquals(RiskSeverity.CAUTION, violation.severity());
        assertTrue(violation.detail().contains("위반건축물"));
        assertTrue(seizure.detail().contains("압류"));
    }

    @Test
    void 지방은_보증금_한도가_5억이다() {
        RiskSignal signal = evaluate("부산광역시 해운대구 우동 1", 0L, 0L, PriorDepositSource.OFFICIAL_DOCUMENT,
                600_000_000L, new OfficialHousePrice(3_000_000_000L, 2026), null, List.of());

        assertTrue(signal.detail().contains("한도 500,000,000원"));
    }

    @Test
    void 공시가격이나_서류로_확인한_선순위_보증금이_없으면_계산하지_않는다() {
        RiskSignal noPrice = evaluate("서울특별시 관악구 봉천동 1", 0L, 0L, PriorDepositSource.OFFICIAL_DOCUMENT,
                100_000_000L, null, null, List.of());
        RiskSignal unverified = evaluate("서울특별시 관악구 봉천동 1", 0L, 0L, PriorDepositSource.LANDLORD_CLAIM,
                100_000_000L, OFFICIAL, null, List.of());

        assertEquals(RiskSeverity.INFO, noPrice.severity());
        assertTrue(noPrice.detail().contains("공시가격이 조회되지 않아"));
        assertTrue(unverified.detail().contains("서류로 확인한 값이 없어"));
    }

    @Test
    void 다가구가_아니면_평가하지_않는다() {
        RegistryAnalysis registry = new RegistryAnalysis("서울특별시", "1", List.of(), List.of(), List.of(), 0L);
        assertTrue(rule.evaluate(new RiskAssessmentInput(
                registry, ContractType.JEONSE, 100_000_000L, null, null, null, null)).isEmpty());
    }
}
