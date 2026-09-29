package com.projectguard.backend.api;

import com.projectguard.backend.registry.RegistryAnalysis;
import com.projectguard.backend.risk.RiskSignal;

import java.util.List;

/**
 * @param registry     등기부 파싱 결과
 * @param marketPrice  조회된 시세 (원). 조회 실패 시 null — 이 경우 시세 비교 규칙은 평가에서 제외됨.
 * @param riskSignals  위험 신호 카드 목록
 * @param hasHighRisk  HIGH 등급 신호가 하나라도 있는지 여부
 * @param disclaimer   결과 화면에 노출해야 하는 면책 문구 (docs/결정사항.md 4번 참고)
 */
public record AnalyzeResponse(
        RegistryAnalysis registry,
        Long marketPrice,
        List<RiskSignal> riskSignals,
        boolean hasHighRisk,
        String disclaimer
) {
}
