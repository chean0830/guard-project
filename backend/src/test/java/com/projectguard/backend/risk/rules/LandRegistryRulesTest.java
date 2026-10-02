package com.projectguard.backend.risk.rules;

import com.projectguard.backend.common.ContractType;
import com.projectguard.backend.common.PriorDepositSource;
import com.projectguard.backend.common.PropertyType;
import com.projectguard.backend.registry.LandRegistryComparison;
import com.projectguard.backend.registry.MortgageEntry;
import com.projectguard.backend.registry.OwnershipEntry;
import com.projectguard.backend.registry.OwnershipType;
import com.projectguard.backend.registry.RegistryAnalysis;
import com.projectguard.backend.registry.RegistryKind;
import com.projectguard.backend.registry.SeizureEntry;
import com.projectguard.backend.registry.SeizureType;
import com.projectguard.backend.risk.RiskAssessmentInput;
import com.projectguard.backend.risk.RiskSeverity;
import com.projectguard.backend.risk.RiskSignal;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 다가구주택 토지 등기부 대조 (공동담보 중복 제거, 소유자·압류·주소 규칙). */
class LandRegistryRulesTest {

    private static final MortgageEntry JOINT = new MortgageEntry(1, 300_000_000L, "홍길동", "테스트은행", "2020년1월15일", false);
    private static final MortgageEntry LAND_ONLY = new MortgageEntry(2, 100_000_000L, "홍길동", "테스트캐피탈", "2022년3월2일", false);
    private static final MortgageEntry LAND_CANCELLED = new MortgageEntry(3, 50_000_000L, "홍길동", "테스트저축", "2021년1월1일", true);

    private RegistryAnalysis building(String owner, List<MortgageEntry> mortgages) {
        long total = mortgages.stream().filter(m -> !m.cancelled()).mapToLong(MortgageEntry::maxClaimAmount).sum();
        return new RegistryAnalysis("서울특별시 관악구 봉천동 123-4", "건물", owners(owner), mortgages, List.of(), total,
                RegistryKind.BUILDING);
    }

    private RegistryAnalysis land(String address, String owner, List<MortgageEntry> mortgages, List<SeizureEntry> seizures) {
        long total = mortgages.stream().filter(m -> !m.cancelled()).mapToLong(MortgageEntry::maxClaimAmount).sum();
        return new RegistryAnalysis(address, "토지", owners(owner), mortgages, seizures, total, RegistryKind.LAND);
    }

    private List<OwnershipEntry> owners(String owner) {
        return List.of(new OwnershipEntry(1, OwnershipType.OWNERSHIP_TRANSFER, owner, "2019년1월1일", false));
    }

    private RiskAssessmentInput input(RegistryAnalysis building, RegistryAnalysis land) {
        return new RiskAssessmentInput(building, ContractType.JEONSE, 100_000_000L, null, 1_000_000_000L, null, null,
                PropertyType.MULTI_HOUSEHOLD, 300_000_000L, PriorDepositSource.OFFICIAL_DOCUMENT, null, null, null, land);
    }

    @Test
    void 공동담보는_한_번만_세고_토지에만_있는_활성_근저당만_더한다() {
        RegistryAnalysis b = building("홍길동", List.of(JOINT));
        RegistryAnalysis l = land("서울특별시 관악구 봉천동 123-4", "홍길동", List.of(JOINT, LAND_ONLY, LAND_CANCELLED), List.of());

        assertEquals(List.of(LAND_ONLY), LandRegistryComparison.landOnlyActiveMortgages(b, l));
        assertEquals(0L, LandRegistryComparison.landOnlyActiveMortgageAmount(b, null));
    }

    @Test
    void 다가구_계산에_토지에만_있는_근저당이_포함된다() {
        // 건물 3억 + 토지만 1억 + 선순위 3억 + 보증금 1억 = 8억 / 10억 = 80% -> HIGH (토지 없이 계산하면 70%)
        RegistryAnalysis b = building("홍길동", List.of(JOINT));
        RegistryAnalysis l = land("서울특별시 관악구 봉천동 123-4", "홍길동", List.of(JOINT, LAND_ONLY), List.of());

        RiskSignal signal = new MultiHouseholdPriorDepositRule().evaluate(input(b, l)).orElseThrow();

        assertEquals(RiskSeverity.HIGH, signal.severity());
        assertTrue(signal.detail().contains("토지에만 있는 100,000,000원"));
    }

    @Test
    void 토지와_건물_소유자가_다르면_HIGH() {
        RiskSignal signal = new LandOwnerMismatchRule().evaluate(input(
                building("홍길동", List.of()), land("서울특별시 관악구 봉천동 123-4", "김철수", List.of(), List.of())))
                .orElseThrow();

        assertEquals(RiskSeverity.HIGH, signal.severity());
        assertTrue(signal.detail().contains("김철수"));
    }

    @Test
    void 토지에_말소되지_않은_압류가_있으면_HIGH() {
        RegistryAnalysis l = land("서울특별시 관악구 봉천동 123-4", "홍길동", List.of(), List.of(
                new SeizureEntry(2, SeizureType.PROVISIONAL_SEIZURE, "2024년5월3일", false),
                new SeizureEntry(3, SeizureType.SEIZURE, "2023년1월1일", true)));

        RiskSignal signal = new LandSeizureRule().evaluate(input(building("홍길동", List.of()), l)).orElseThrow();

        assertEquals(RiskSeverity.HIGH, signal.severity());
        assertTrue(signal.detail().contains("1건"));
    }

    @Test
    void 토지_주소가_건물과_다르면_CAUTION() {
        RegistryAnalysis other = land("서울특별시 관악구 봉천동 999", "홍길동", List.of(), List.of());

        assertEquals(RiskSeverity.CAUTION, new LandAddressMismatchRule()
                .evaluate(input(building("홍길동", List.of()), other)).orElseThrow().severity());
    }

    @Test
    void 토지_등기부가_없거나_문제가_없으면_신호가_없다() {
        RegistryAnalysis b = building("홍길동", List.of());
        RegistryAnalysis clean = land("서울특별시 관악구 봉천동 123-4", "홍길동", List.of(), List.of());

        for (var rule : List.of(new LandOwnerMismatchRule(), new LandSeizureRule(), new LandAddressMismatchRule())) {
            assertTrue(rule.evaluate(input(b, null)).isEmpty());
            assertTrue(rule.evaluate(input(b, clean)).isEmpty());
        }
    }
}
