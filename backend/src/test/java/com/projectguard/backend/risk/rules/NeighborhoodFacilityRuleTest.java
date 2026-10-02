package com.projectguard.backend.risk.rules;

import com.projectguard.backend.common.ContractType;
import com.projectguard.backend.common.PropertyType;
import com.projectguard.backend.market.BuildingInfo;
import com.projectguard.backend.market.BuildingInfo.FloorUse;
import com.projectguard.backend.registry.RegistryAnalysis;
import com.projectguard.backend.risk.RiskAssessmentInput;
import com.projectguard.backend.risk.RiskSeverity;
import com.projectguard.backend.risk.RiskSignal;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 층 구성은 실제 건축물대장 응답(관악구 상가·상가주택)을 본떠 만들었다. */
class NeighborhoodFacilityRuleTest {

    private final NeighborhoodFacilityRule rule = new NeighborhoodFacilityRule();

    private BuildingInfo building(String code, String mainPurpose, String etcPurpose, List<FloorUse> floors) {
        return new BuildingInfo("테스트빌", mainPurpose, "철근콘크리트구조", "20200101", 300.0, code, etcPurpose, floors);
    }

    private FloorUse floor(int no, String code, String name, String etc) {
        return new FloorUse(false, no, no + "층", code, name, etc);
    }

    private Optional<RiskSignal> evaluate(PropertyType type, ContractType contract, BuildingInfo building) {
        RegistryAnalysis registry = new RegistryAnalysis("주소", "고유번호", List.of(), List.of(), List.of(), 0L);
        return rule.evaluate(new RiskAssessmentInput(
                registry, contract, 100_000_000L, contract == ContractType.WOLSE ? 500_000L : null, null, null, null,
                type, null, null, building));
    }

    @Test
    void 주택_층이_없는_근린생활시설_건물이고_전세면_HIGH() {
        BuildingInfo commercial = building("04000", "제2종근린생활시설", null, List.of(
                floor(2, "04010", "학원", "제2종근린생활시설(학원)"),
                // 이름에 "근린생활시설"이 없어도 코드(03xxx)로 판별한다
                floor(3, "03005", "의원", "의원")));

        Optional<RiskSignal> result = evaluate(PropertyType.MULTI_HOUSEHOLD, ContractType.JEONSE, commercial);

        assertEquals(RiskSeverity.HIGH, result.orElseThrow().severity());
        assertTrue(result.get().detail().contains("보증보험"));
    }

    @Test
    void 주택_층이_없는_근린생활시설_건물이고_월세면_CAUTION() {
        Optional<RiskSignal> result = evaluate(PropertyType.OFFICETEL, ContractType.WOLSE,
                building("04000", "제2종근린생활시설", null, List.of()));

        assertEquals(RiskSeverity.CAUTION, result.orElseThrow().severity());
    }

    @Test
    void 상가주택은_HIGH가_아니라_근생_층과_주택_층을_알려주는_CAUTION() {
        BuildingInfo shopHouse = building("03000", "제1종근린생활시설", "점포, 주택", List.of(
                floor(2, "03001", "소매점", "점포"),
                floor(1, "01001", "단독주택", "주택"),
                floor(1, "03001", "소매점", "점포"),
                new FloorUse(false, 1, "옥탑1층", "03999", "기타제1종근린생활시설", null)));

        Optional<RiskSignal> result = evaluate(PropertyType.MULTI_HOUSEHOLD, ContractType.JEONSE, shopHouse);

        assertEquals(RiskSeverity.CAUTION, result.orElseThrow().severity());
        assertTrue(result.get().detail().contains("1층(점포), 2층(점포)"));
        assertTrue(result.get().detail().contains("주택으로 등록된 층은 1층(주택)"));
        assertFalse(result.get().detail().contains("옥탑"));
    }

    @Test
    void 층별개요가_없어도_기타용도에_주택이_있으면_상가주택으로_본다() {
        Optional<RiskSignal> result = evaluate(PropertyType.MULTI_HOUSEHOLD, ContractType.JEONSE,
                building("03000", "제1종근린생활시설", "점포, 주택", List.of()));

        // 층 목록이 없어 어느 층이 근생인지 알 수 없으니 신호를 내지 않는다
        assertTrue(result.isEmpty());
    }

    @Test
    void 빌라_일부_층이_근린생활시설이면_CAUTION() {
        Optional<RiskSignal> result = evaluate(PropertyType.VILLA, ContractType.JEONSE, building("02000", "공동주택", null,
                List.of(floor(1, "04010", "사무소", "제2종근린생활시설(사무소)"),
                        floor(2, "02003", "다세대주택", "다세대주택"))));

        assertEquals(RiskSeverity.CAUTION, result.orElseThrow().severity());
        assertTrue(result.get().detail().contains("1층(제2종근린생활시설(사무소))"));
    }

    @Test
    void 아파트는_저층_상가만으로는_알리지_않는다() {
        Optional<RiskSignal> result = evaluate(PropertyType.APARTMENT, ContractType.JEONSE, building("02000", "공동주택", null,
                List.of(floor(1, "03001", "소매점", "소매점"), floor(2, "02001", "아파트", "아파트"))));

        assertTrue(result.isEmpty());
    }

    @Test
    void 주택만_있거나_건축물대장이_없으면_신호가_없다() {
        assertTrue(evaluate(PropertyType.MULTI_HOUSEHOLD, ContractType.JEONSE, building("01000", "단독주택",
                "다가구주택(5가구)", List.of(floor(1, "01003", "다가구주택", "다가구주택")))).isEmpty());
        assertTrue(evaluate(PropertyType.MULTI_HOUSEHOLD, ContractType.JEONSE, null).isEmpty());
    }
}
