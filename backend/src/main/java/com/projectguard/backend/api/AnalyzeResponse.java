package com.projectguard.backend.api;

import com.projectguard.backend.checklist.ChecklistItem;
import com.projectguard.backend.common.PropertyType;
import com.projectguard.backend.market.BuildingInfo;
import com.projectguard.backend.market.OfficialHousePrice;
import com.projectguard.backend.registry.RegistryAnalysis;
import com.projectguard.backend.risk.RiskSignal;

import java.util.List;

/**
 * @param propertyType 요청한 부동산 유형. 다가구주택이면 화면에서 "안전" 대신 조건부 결과로 표시한다.
 * @param registry     등기부 파싱 결과
 * @param landRegistry 함께 올린 토지 등기부 파싱 결과 (다가구 선택). 없으면 null.
 * @param marketPrice  조회된 시세 (원). 다가구주택은 사용자가 입력한 건물 시세. 없으면 null — 이 경우 시세 비교 규칙은 평가에서 제외됨.
 * @param officialHousePrice 단독·다가구 공시가격(개별주택가격). 다가구가 아니거나 조회 실패 시 null.
 * @param buildingInfo 건축물대장 표제부 조회 결과. 조회 실패 시 null — 등록되지 않은 건물이라는
 *                     뜻일 수도, 주소 변환 실패일 수도 있어 위험 신호로 단정하지 않고 참고 정보로만 제공.
 * @param riskSignals  위험 신호 카드 목록
 * @param hasHighRisk  HIGH 등급 신호가 하나라도 있는지 여부
 * @param checklist    서류만으로는 알 수 없어 사용자가 직접 확인해야 하는 할 일 목록
 * @param disclaimer   결과 화면에 노출해야 하는 면책 문구 (docs/결정사항.md 4번 참고)
 */
public record AnalyzeResponse(
        PropertyType propertyType,
        RegistryAnalysis registry,
        RegistryAnalysis landRegistry,
        Long marketPrice,
        OfficialHousePrice officialHousePrice,
        BuildingInfo buildingInfo,
        List<RiskSignal> riskSignals,
        boolean hasHighRisk,
        List<ChecklistItem> checklist,
        String disclaimer
) {
}
