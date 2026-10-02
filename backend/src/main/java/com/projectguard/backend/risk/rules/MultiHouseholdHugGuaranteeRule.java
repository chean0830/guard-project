package com.projectguard.backend.risk.rules;

import com.projectguard.backend.common.PriorDepositSource;
import com.projectguard.backend.common.ViolationBuildingAnswer;
import com.projectguard.backend.market.BuildingInfo;
import com.projectguard.backend.market.OfficialHousePrice;
import com.projectguard.backend.registry.RegistryAnalysis;
import com.projectguard.backend.risk.HugGuaranteeCriteria;
import com.projectguard.backend.risk.RiskAssessmentInput;
import com.projectguard.backend.risk.RiskRule;
import com.projectguard.backend.risk.RiskSeverity;
import com.projectguard.backend.risk.RiskSignal;
import com.projectguard.backend.risk.RiskSource;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 다가구주택이 HUG 전세보증금반환보증 가입 기준에 드는지 추정한다. 보증에 가입할 수 없는 집은
 * 집주인이 보증금을 못 돌려줄 때 기댈 곳이 크게 줄어, 계약 전에 미리 알아야 한다.
 *
 * HUG는 다가구 주택가격을 공시가격 × 140%로 보고, 근저당 60% / 근저당+다른 세입자 보증금 80% /
 * 전부+내 보증금 90% 이내인지 본다 (수치는 HugGuaranteeCriteria). 위반건축물·근린생활시설·압류 등은
 * 금액과 상관없이 가입이 안 된다. 실제 심사는 감정평가액 등을 쓸 수 있어 "추정"으로만 안내한다.
 *
 *  - 가입이 어려운 사유가 하나라도 있으면 CAUTION (원인 자체는 다른 규칙이 HIGH로 따로 알린다)
 *  - 기준 안이면 INFO (가입 가능성 있음, 확정 아님)
 *  - 공시가격이나 선순위 보증금(서류 확인)을 몰라 계산할 수 없으면 INFO로 확인 방법만 안내
 */
@Component
public class MultiHouseholdHugGuaranteeRule implements RiskRule {

    private static final String CODE = "MULTI_HOUSEHOLD_HUG_GUARANTEE";
    private static final String SOURCE_DESCRIPTION =
            "HUG 전세보증금반환보증 가입 기준 (2026년 10월 확인한 공개 기준으로 추정, 실제 심사 결과와 다를 수 있음)";
    private static final String HOW_TO_CHECK =
            " 정확한 가입 가능 여부는 계약 전에 HUG 안심전세 앱이나 HUG 고객센터(1566-9009)에서 확인하세요.";

    @Override
    public Optional<RiskSignal> evaluate(RiskAssessmentInput input) {
        if (!input.isMultiHousehold()) {
            return Optional.empty();
        }

        List<String> blockers = blockers(input);
        OfficialHousePrice official = input.officialHousePrice();
        boolean priorVerified = input.priorDepositSource() == PriorDepositSource.OFFICIAL_DOCUMENT
                && input.priorDepositTotal() != null;

        if (official == null || official.price() <= 0 || !priorVerified) {
            String missing = official == null || official.price() <= 0
                    ? "이 건물 공시가격이 조회되지 않아"
                    : "먼저 들어온 세입자 보증금을 서류로 확인한 값이 없어";
            if (!blockers.isEmpty()) {
                return Optional.of(signal(RiskSeverity.CAUTION, "HUG 전세보증보험 가입이 어려울 수 있어요",
                        "금액과 상관없이 가입을 막는 사유가 있어요: " + String.join(", ", blockers) + ". "
                                + missing + " 금액 기준은 계산하지 못했어요." + HOW_TO_CHECK));
            }
            return Optional.of(signal(RiskSeverity.INFO, "HUG 전세보증보험 가입 가능성을 계산하지 못했어요",
                    missing + " 가입 기준(공시가격의 140%를 주택가격으로 보고, 근저당·다른 세입자 보증금·내 보증금 합계가 "
                            + "그 90% 이내)을 계산하지 못했어요." + HOW_TO_CHECK));
        }

        long housePrice = Math.round(official.price() * HugGuaranteeCriteria.OFFICIAL_PRICE_MULTIPLIER);
        long mortgage = MultiHouseholdPriceBasis.seniorMortgage(input);
        long prior = input.priorDepositTotal();
        long deposit = input.depositAmount();

        if (mortgage > housePrice * HugGuaranteeCriteria.SENIOR_DEBT_RATIO_LIMIT) {
            blockers.add(String.format("근저당 %,d원이 기준 주택가격의 60%%(%,d원)를 넘어요",
                    mortgage, Math.round(housePrice * HugGuaranteeCriteria.SENIOR_DEBT_RATIO_LIMIT)));
        }
        if (mortgage + prior > housePrice * HugGuaranteeCriteria.SENIOR_DEBT_PLUS_PRIOR_DEPOSIT_RATIO_LIMIT) {
            blockers.add(String.format("근저당과 다른 세입자 보증금 합계 %,d원이 기준 주택가격의 80%%(%,d원)를 넘어요",
                    mortgage + prior,
                    Math.round(housePrice * HugGuaranteeCriteria.SENIOR_DEBT_PLUS_PRIOR_DEPOSIT_RATIO_LIMIT)));
        }
        long total = mortgage + prior + deposit;
        long totalLimit = Math.round(housePrice * HugGuaranteeCriteria.TOTAL_RATIO_LIMIT);
        if (total > totalLimit) {
            blockers.add(String.format("근저당·다른 세입자 보증금·내 보증금 합계 %,d원이 기준 주택가격의 90%%(%,d원)를 넘어요",
                    total, totalLimit));
        }

        String basis = String.format("HUG는 이 건물 주택가격을 공시가격 %,d원(%d년)의 140%%인 %,d원으로 볼 가능성이 높아요. ",
                official.price(), official.year(), housePrice);

        if (blockers.isEmpty()) {
            return Optional.of(signal(RiskSeverity.INFO, "HUG 전세보증보험 가입 기준 안에 드는 것으로 보여요 (추정)",
                    basis + String.format("근저당·다른 세입자 보증금(입력값)·내 보증금 합계 %,d원이 그 90%%(%,d원) 이내예요. "
                            + "다만 입력값이 맞다는 전제의 추정이고, 실제 심사는 다를 수 있어요.", total, totalLimit)
                            + HOW_TO_CHECK));
        }
        return Optional.of(signal(RiskSeverity.CAUTION, "HUG 전세보증보험 가입이 어려울 수 있어요",
                basis + "가입을 막을 수 있는 사유: " + String.join(", ", blockers) + ". "
                        + "보증보험에 가입할 수 없으면 집주인이 보증금을 못 돌려줄 때 기댈 곳이 크게 줄어들어요."
                        + HOW_TO_CHECK));
    }

    /** 금액과 상관없이 가입을 막는 사유. */
    private List<String> blockers(RiskAssessmentInput input) {
        List<String> blockers = new ArrayList<>();

        long cap = HugGuaranteeCriteria.isCapitalArea(input.registry().address())
                ? HugGuaranteeCriteria.DEPOSIT_CAP_CAPITAL_AREA
                : HugGuaranteeCriteria.DEPOSIT_CAP_OTHER;
        if (input.depositAmount() > cap) {
            blockers.add(String.format("보증금이 이 지역 한도 %,d원을 넘어요", cap));
        }
        if (input.violationBuilding() == ViolationBuildingAnswer.MARKED) {
            blockers.add("건축물대장에 위반건축물 표시가 있어요");
        }
        BuildingInfo building = input.buildingInfo();
        if (building != null && building.isNeighborhoodFacilityBuilding() && !building.hasHousingPart()) {
            blockers.add("건축물대장상 주택이 아닌 근린생활시설이에요");
        }
        if (hasUnresolvedSeizure(input.registry()) || hasUnresolvedSeizure(input.landRegistry())) {
            blockers.add("말소되지 않은 압류·가압류·경매 기록이 있어요");
        }
        return blockers;
    }

    private boolean hasUnresolvedSeizure(RegistryAnalysis registry) {
        return registry != null && registry.seizures().stream().anyMatch(s -> !s.cancelled());
    }

    private RiskSignal signal(RiskSeverity severity, String title, String detail) {
        return new RiskSignal(CODE, title, severity, RiskSource.GOVERNMENT_GUIDELINE, SOURCE_DESCRIPTION, detail);
    }
}
