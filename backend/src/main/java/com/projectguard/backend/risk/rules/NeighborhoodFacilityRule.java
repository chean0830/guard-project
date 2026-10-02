package com.projectguard.backend.risk.rules;

import com.projectguard.backend.common.ContractType;
import com.projectguard.backend.common.PropertyType;
import com.projectguard.backend.market.BuildingInfo;
import com.projectguard.backend.risk.RiskAssessmentInput;
import com.projectguard.backend.risk.RiskRule;
import com.projectguard.backend.risk.RiskSeverity;
import com.projectguard.backend.risk.RiskSignal;
import com.projectguard.backend.risk.RiskSource;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * 건축물대장상 근린생활시설(상가·사무실 용도)을 원룸 등 주거용으로 개조해 임대하는 경우를 찾는다.
 * 주거용으로 쓰면 건축법상 용도변경 위반이라 위반건축물로 등재·이행강제금 부과 대상이 될 수 있고,
 * 전세자금대출과 전세보증금 반환보증 가입이 막히는 경우가 많다.
 *
 *  - 주택으로 등록된 부분이 전혀 없는 건물: 전세는 HIGH(보증 가입이 막히면 보증금을 지킬 수단이 크게 줄어듦), 월세는 CAUTION
 *  - 주택 층과 근린생활시설 층이 섞인 건물(상가주택 등): CAUTION — 계약하는 집이 어느 층인지는 서비스가 알 수 없어
 *    양쪽 층 목록을 보여준다. 주용도가 주택인 아파트·오피스텔은 저층 상가가 흔해 알리지 않는다.
 * 건축물대장 조회에 실패했으면 판단하지 않는다.
 */
@Component
public class NeighborhoodFacilityRule implements RiskRule {

    private static final String CODE = "NEIGHBORHOOD_FACILITY_AS_HOUSING";
    private static final String SOURCE_DESCRIPTION = "건축물대장 표제부·층별개요 (국토교통부 건축HUB)";
    private static final String CONSEQUENCES =
            "근린생활시설을 주거용으로 쓰면 건축법상 용도변경 위반이라 위반건축물로 등재되거나 이행강제금이 부과될 수 있고, "
                    + "전세자금대출과 전세보증금 반환보증(HUG·SGI) 가입이 거절되는 경우가 많아요. "
                    + "실제로 주거로 썼다면 주택임대차보호법 보호를 받을 수 있다는 판례가 있지만, 문제가 생기면 다퉈야 할 수 있어요.";

    @Override
    public Optional<RiskSignal> evaluate(RiskAssessmentInput input) {
        BuildingInfo building = input.buildingInfo();
        if (building == null) {
            return Optional.empty();
        }

        if (building.isNeighborhoodFacilityBuilding() && !building.hasHousingPart()) {
            boolean jeonse = input.contractType() == ContractType.JEONSE;
            return Optional.of(new RiskSignal(
                    CODE,
                    "건축물대장상 주택이 아니라 근린생활시설이에요",
                    jeonse ? RiskSeverity.HIGH : RiskSeverity.CAUTION,
                    RiskSource.FACTUAL,
                    SOURCE_DESCRIPTION,
                    String.format("이 건물의 주용도는 '%s'이고, 주택으로 등록된 층이 없어요. "
                                    + "상가·사무실 용도 건물을 원룸 등으로 개조해 임대하는 경우일 수 있어요. ",
                            building.mainPurpose())
                            + CONSEQUENCES
                            + (jeonse ? " 전세라면 계약 전에 보증보험 가입이 되는지 꼭 먼저 확인하세요." : "")));
        }

        boolean residentialType =
                input.propertyType() == PropertyType.VILLA || input.propertyType() == PropertyType.MULTI_HOUSEHOLD;
        List<String> commercialFloors = building.neighborhoodFacilityFloorLabels();
        if (commercialFloors.isEmpty() || !(residentialType || building.isNeighborhoodFacilityBuilding())) {
            return Optional.empty();
        }

        List<String> housingFloors = building.housingFloorLabels();
        String housingPart = housingFloors.isEmpty()
                ? ""
                : " 주택으로 등록된 층은 " + String.join(", ", housingFloors) + "이에요.";
        return Optional.of(new RiskSignal(
                CODE,
                "건물 일부 층이 근린생활시설이에요",
                RiskSeverity.CAUTION,
                RiskSource.FACTUAL,
                SOURCE_DESCRIPTION,
                "건축물대장상 " + String.join(", ", commercialFloors) + "이(가) 주택이 아닌 근린생활시설로 등록돼 있어요."
                        + housingPart
                        + " 계약하는 집이 근린생활시설 층에 있다면 주의가 필요해요. " + CONSEQUENCES
                        + " 계약서의 층·호수와 건축물대장의 층별 용도를 대조해보세요."));
    }
}
