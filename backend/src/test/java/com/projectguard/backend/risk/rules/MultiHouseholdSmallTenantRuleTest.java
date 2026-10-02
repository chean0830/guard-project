package com.projectguard.backend.risk.rules;

import com.projectguard.backend.common.ContractType;
import com.projectguard.backend.common.PriorDepositSource;
import com.projectguard.backend.common.PropertyType;
import com.projectguard.backend.market.BuildingInfo;
import com.projectguard.backend.market.OfficialHousePrice;
import com.projectguard.backend.registry.RegistryAnalysis;
import com.projectguard.backend.risk.RiskAssessmentInput;
import com.projectguard.backend.risk.RiskSeverity;
import com.projectguard.backend.risk.RiskSignal;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 서울 기준: 소액임차인 보증금 1억6,500만 원 이하, 최우선변제금 5,500만 원. */
class MultiHouseholdSmallTenantRuleTest {

    private final MultiHouseholdSmallTenantRule rule = new MultiHouseholdSmallTenantRule();

    private BuildingInfo building(Integer familyCount) {
        return new BuildingInfo("테스트빌", "단독주택", null, null, null, "01000", "다가구주택", List.of(), familyCount);
    }

    private Optional<RiskSignal> evaluate(
            long deposit, Long buildingPrice, Long prior, Integer familyCount, Integer roomCount, OfficialHousePrice official
    ) {
        RegistryAnalysis registry = new RegistryAnalysis(
                "서울특별시 관악구 봉천동 1", "고유번호", List.of(), List.of(), List.of(), 200_000_000L);
        return rule.evaluate(new RiskAssessmentInput(registry, ContractType.JEONSE, deposit, null, buildingPrice,
                null, null, PropertyType.MULTI_HOUSEHOLD, prior, PriorDepositSource.OFFICIAL_DOCUMENT,
                familyCount == null ? null : building(familyCount), null, official, null, roomCount));
    }

    @Test
    void 다른_방이_모두_소액임차인인_최악의_경우_위험_수준이면_CAUTION() {
        // 7가구 -> 다른 방 6 x 5,500만 = 3억3천 (건물값 10억의 절반 이하)
        // 근저당 2억 + 선순위 2억 + 3억3천 + 보증금 2억 = 9억3천 / 10억 = 93%
        RiskSignal signal = evaluate(200_000_000L, 1_000_000_000L, 200_000_000L, 7, null, null).orElseThrow();

        assertEquals(RiskSeverity.CAUTION, signal.severity());
        assertTrue(signal.detail().contains("방이 7개예요(건축물대장 가구수)"));
        assertTrue(signal.detail().contains("330,000,000원"));
    }

    @Test
    void 비율이_낮으면_INFO로_안내만_한다() {
        // 3가구 -> 1억1천. 2억 + 0 + 1억1천 + 2억 = 5억1천 / 20억 = 25.5%
        RiskSignal signal = evaluate(200_000_000L, 2_000_000_000L, 0L, 3, null, null).orElseThrow();

        assertEquals(RiskSeverity.INFO, signal.severity());
    }

    @Test
    void 최우선변제_총액은_건물값의_절반을_넘지_않는다() {
        // 20가구 -> 19 x 5,500만 = 10억4,500만이지만 공시가격 6억의 절반 3억으로 제한
        RiskSignal signal = evaluate(200_000_000L, null, 0L, 20, null, new OfficialHousePrice(600_000_000L, 2026))
                .orElseThrow();

        assertTrue(signal.detail().contains("300,000,000원(건물값의 절반 한도 적용)"));
        assertTrue(signal.detail().contains("공시가격 600,000,000원"));
    }

    @Test
    void 입력한_방_수가_가구수보다_많으면_방_쪼개기를_알린다() {
        RiskSignal signal = evaluate(200_000_000L, 2_000_000_000L, 0L, 5, 9, null).orElseThrow();

        assertTrue(signal.detail().contains("방이 9개예요(입력값)"));
        assertTrue(signal.detail().contains("방 쪼개기"));
    }

    @Test
    void 내_보증금도_소액이면_나눠_받는다고_안내한다() {
        RiskSignal signal = evaluate(100_000_000L, 2_000_000_000L, 0L, 5, null, null).orElseThrow();

        assertTrue(signal.detail().contains("내 보증금도 소액임차인 기준 안"));
    }

    @Test
    void 방_수를_모르거나_한_가구뿐이면_평가하지_않는다() {
        assertTrue(evaluate(200_000_000L, 1_000_000_000L, 0L, null, null, null).isEmpty());
        assertTrue(evaluate(200_000_000L, 1_000_000_000L, 0L, 1, null, null).isEmpty());
    }
}
