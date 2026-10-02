package com.projectguard.backend.risk.rules;

import com.projectguard.backend.common.ContractType;
import com.projectguard.backend.common.ViolationBuildingAnswer;
import com.projectguard.backend.risk.RiskAssessmentInput;
import com.projectguard.backend.risk.RiskRule;
import com.projectguard.backend.risk.RiskSeverity;
import com.projectguard.backend.risk.RiskSignal;
import com.projectguard.backend.risk.RiskSource;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * 건축물대장에 "위반건축물" 표시가 있으면 전세자금대출·전세보증금 반환보증 가입이 거절되는 경우가 많고,
 * 이행강제금 부담으로 임대인 재정이 나빠질 수 있다. 위반건축물 여부는 공공 API로 받을 수 없어
 * 사용자가 직접 확인해 입력한 값으로만 판단한다 — 확인하지 않았으면 체크리스트로 확인 방법을 안내한다.
 * 전세는 보증 가입이 막히면 보증금을 지킬 수단이 크게 줄어 HIGH, 월세는 CAUTION.
 */
@Component
public class ViolationBuildingRule implements RiskRule {

    @Override
    public Optional<RiskSignal> evaluate(RiskAssessmentInput input) {
        if (input.violationBuilding() != ViolationBuildingAnswer.MARKED) {
            return Optional.empty();
        }

        boolean jeonse = input.contractType() == ContractType.JEONSE;
        return Optional.of(new RiskSignal(
                "VIOLATION_BUILDING",
                "건축물대장에 위반건축물 표시가 있어요",
                jeonse ? RiskSeverity.HIGH : RiskSeverity.CAUTION,
                RiskSource.FACTUAL,
                "건축물대장 (사용자가 직접 확인해 입력한 값)",
                "위반건축물은 불법 증축·용도변경 등으로 건축법을 어긴 건물이에요. 전세자금대출과 전세보증금 반환보증(HUG·SGI) "
                        + "가입이 거절되는 경우가 많고, 이행강제금이 계속 부과돼 임대인 부담이 커질 수 있어요. "
                        + "건축물대장의 '변동사항'란에서 어떤 위반인지(무단 증축, 용도변경 등)와 계약하는 집이 위반 부분인지 확인하세요."
                        + (jeonse ? " 전세라면 계약 전에 보증보험 가입이 되는지 꼭 먼저 확인하세요." : "")));
    }
}
