package com.projectguard.backend.risk.rules;

import com.projectguard.backend.common.PriorDepositSource;
import com.projectguard.backend.registry.LandRegistryComparison;
import com.projectguard.backend.risk.RiskAssessmentInput;
import com.projectguard.backend.risk.RiskRule;
import com.projectguard.backend.risk.RiskSeverity;
import com.projectguard.backend.risk.RiskSignal;
import com.projectguard.backend.risk.RiskSource;
import com.projectguard.backend.risk.RiskThresholds;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * 다가구주택 전용 규칙. 다가구는 건물 전체가 한 등기부라, 경매로 넘어가면 근저당뿐 아니라
 * 나보다 먼저 들어온 세입자들의 보증금까지 먼저 배당된다. 그런데 이 보증금은 등기부에 나오지 않아
 * 사용자가 전입세대 열람·확정일자 부여현황으로 직접 확인해 입력한 값에만 의존할 수밖에 없다.
 *
 * 입력값이 틀렸을 때 "안전"이 나오는 게 가장 위험하므로, 이 규칙은 다가구주택이면 항상 신호를 낸다.
 *  - 공식 서류로 확인하지 않았으면(임대인 말, 모름) 계산과 상관없이 HIGH
 *  - 건물 시세를 모르면 CAUTION
 *  - 둘 다 있으면 (근저당 + 선순위 보증금 + 내 보증금) / 건물 시세 비율로 판단하되, 낮게 나와도 CAUTION
 */
@Component
public class MultiHouseholdPriorDepositRule implements RiskRule {

    private static final String SOURCE_DESCRIPTION =
            "국토교통부/금융감독원 전세사기 예방 안내자료 참고 (법적 기준 아님, 참고 지표). "
                    + "선순위 보증금·건물 시세는 사용자가 입력한 값이며 서비스가 확인하지 않았습니다. "
                    + "건물 시세를 입력하지 않았으면 공시가격(국토교통부 개별주택가격)을 기준으로 계산합니다";

    @Override
    public Optional<RiskSignal> evaluate(RiskAssessmentInput input) {
        if (!input.isMultiHousehold()) {
            return Optional.empty();
        }

        PriorDepositSource source = input.priorDepositSource() != null
                ? input.priorDepositSource() : PriorDepositSource.UNKNOWN;
        Long priorDeposit = input.priorDepositTotal();
        MultiHouseholdPriceBasis basis = MultiHouseholdPriceBasis.of(input);
        // 토지 등기부를 함께 올렸으면, 토지에만 걸린 근저당도 경매 시 먼저 배당되므로 더한다 (공동담보는 한 번만).
        long landOnlyMortgage = LandRegistryComparison.landOnlyActiveMortgageAmount(input.registry(), input.landRegistry());
        long mortgage = input.registry().totalActiveMortgageAmount() + landOnlyMortgage;
        String mortgageLabel = landOnlyMortgage > 0
                ? String.format("근저당 %,d원(건물 %,d원 + 토지에만 있는 %,d원)",
                        mortgage, input.registry().totalActiveMortgageAmount(), landOnlyMortgage)
                : String.format("근저당 %,d원", mortgage);

        if (source != PriorDepositSource.OFFICIAL_DOCUMENT || priorDeposit == null) {
            return Optional.of(unverified(source, priorDeposit, basis, mortgage, input.depositAmount()));
        }

        if (basis == null) {
            return Optional.of(new RiskSignal(
                    "MULTI_HOUSEHOLD_PRICE_UNKNOWN",
                    "건물 시세를 몰라 보증금 회수 가능성을 계산하지 못함",
                    RiskSeverity.CAUTION,
                    RiskSource.GOVERNMENT_GUIDELINE,
                    SOURCE_DESCRIPTION,
                    String.format(
                            "%s + 먼저 들어온 세입자 보증금 %,d원(입력값) + 내 보증금 %,d원 = %,d원이 "
                                    + "경매 시 나보다 먼저이거나 함께 배당받는 금액입니다. 건물 시세(매매가)를 알아야 이 금액이 "
                                    + "안전한 수준인지 판단할 수 있어요. 공시가격도 조회되지 않았으니, 인근 다가구 매매 사례나 감정평가액을 확인해보세요.",
                            mortgageLabel, priorDeposit, input.depositAmount(),
                            mortgage + priorDeposit + input.depositAmount())));
        }

        long combined = mortgage + priorDeposit + input.depositAmount();
        double ratio = (double) combined / basis.price();
        String formula = String.format(
                "%s + 먼저 들어온 세입자 보증금 %,d원(입력값) + 내 보증금 %,d원 = %,d원으로, %s의 %.1f%%입니다.",
                mortgageLabel, priorDeposit, input.depositAmount(), combined, basis.label(), ratio * 100);

        if (ratio >= RiskThresholds.SENIOR_DEBT_PLUS_DEPOSIT_RATIO_HIGH) {
            return Optional.of(signal("MULTI_HOUSEHOLD_SENIOR_RATIO",
                    "근저당과 선순위 보증금 합계가 건물 시세 대비 높음", RiskSeverity.HIGH,
                    formula + " 이 비율이 높을수록 경매로 넘어갔을 때 보증금을 다 돌려받지 못할 위험이 커집니다."));
        }
        if (ratio >= RiskThresholds.SENIOR_DEBT_PLUS_DEPOSIT_RATIO_CAUTION) {
            return Optional.of(signal("MULTI_HOUSEHOLD_SENIOR_RATIO",
                    "근저당과 선순위 보증금 합계가 건물 시세 대비 다소 높음", RiskSeverity.CAUTION,
                    formula + " 경매 낙찰가는 보통 시세보다 낮아, 이 정도 비율이면 보증금 일부를 못 돌려받을 수 있어요."));
        }
        return Optional.of(signal("MULTI_HOUSEHOLD_SENIOR_RATIO",
                "입력하신 값 기준으로는 비율이 낮음 (조건부)", RiskSeverity.CAUTION,
                formula + " 입력하신 값이 맞다면 낮은 편이지만, 이 결과는 선순위 보증금과 건물 시세가 정확하다는 "
                        + "전제에서만 의미가 있어요. 계약 직전에 전입세대 열람·확정일자 부여현황을 다시 확인하고, "
                        + "소액임차인은 나중에 들어왔더라도 최우선변제로 먼저 배당받을 수 있다는 점도 고려하세요."));
    }

    private RiskSignal unverified(
            PriorDepositSource source, Long priorDeposit, MultiHouseholdPriceBasis basis, long mortgage, long deposit
    ) {
        String title = source == PriorDepositSource.LANDLORD_CLAIM
                ? "먼저 들어온 세입자 보증금을 임대인 말로만 확인함"
                : "먼저 들어온 세입자 보증금을 확인하지 못함";

        StringBuilder detail = new StringBuilder(
                "다가구주택은 경매로 넘어가면 근저당과 나보다 먼저 들어온 세입자들의 보증금이 먼저 배당됩니다. "
                        + "이 보증금은 등기부에 나오지 않아 서류 없이는 위험한지 판단할 수 없어요. ");
        if (source == PriorDepositSource.LANDLORD_CLAIM) {
            detail.append("다가구 전세사기는 임대인이 이 금액을 줄여 말하는 방식으로 자주 일어납니다. ");
        }
        if (priorDeposit != null && basis != null) {
            long combined = mortgage + priorDeposit + deposit;
            detail.append(String.format(
                    "참고로 입력하신 값으로 계산하면 (근저당 + 선순위 보증금 + 내 보증금) %,d원이 %s의 %.1f%%지만, "
                            + "확인되지 않은 값이라 이 비율을 믿으면 안 됩니다. ",
                    combined, basis.label(), (double) combined / basis.price() * 100));
        }
        detail.append("계약 전에 임대인 동의를 받아 주민센터에서 전입세대 열람내역서와 확정일자 부여현황을 꼭 확인하세요.");

        return signal("MULTI_HOUSEHOLD_PRIOR_DEPOSIT_UNVERIFIED", title, RiskSeverity.HIGH, detail.toString());
    }

    private RiskSignal signal(String code, String title, RiskSeverity severity, String detail) {
        return new RiskSignal(code, title, severity, RiskSource.GOVERNMENT_GUIDELINE, SOURCE_DESCRIPTION, detail);
    }
}
