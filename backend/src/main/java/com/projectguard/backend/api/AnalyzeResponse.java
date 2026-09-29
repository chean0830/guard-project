package com.projectguard.backend.api;

import com.projectguard.backend.checklist.ChecklistItem;
import com.projectguard.backend.market.BuildingInfo;
import com.projectguard.backend.registry.RegistryAnalysis;
import com.projectguard.backend.risk.RiskSignal;

import java.util.List;

/**
 * @param registry     등기부 파싱 결과
 * @param marketPrice  조회된 시세 (원). 조회 실패 시 null — 이 경우 시세 비교 규칙은 평가에서 제외됨.
 * @param buildingInfo 건축물대장 표제부 조회 결과. 조회 실패 시 null — 등록되지 않은 건물이라는
 *                     뜻일 수도, 주소 변환 실패일 수도 있어 위험 신호로 단정하지 않고 참고 정보로만 제공.
 * @param riskSignals  위험 신호 카드 목록
 * @param hasHighRisk  HIGH 등급 신호가 하나라도 있는지 여부
 * @param checklist    서류만으로는 알 수 없어 사용자가 직접 확인해야 하는 할 일 목록
 * @param disclaimer   결과 화면에 노출해야 하는 면책 문구 (docs/결정사항.md 4번 참고)
 */
public record AnalyzeResponse(
        RegistryAnalysis registry,
        Long marketPrice,
        BuildingInfo buildingInfo,
        List<RiskSignal> riskSignals,
        boolean hasHighRisk,
        List<ChecklistItem> checklist,
        String disclaimer
) {
}
