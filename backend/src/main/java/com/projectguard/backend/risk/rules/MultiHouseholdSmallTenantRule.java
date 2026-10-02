package com.projectguard.backend.risk.rules;

import com.projectguard.backend.market.BuildingInfo;
import com.projectguard.backend.risk.Region;
import com.projectguard.backend.risk.RegionClassifier;
import com.projectguard.backend.risk.RiskAssessmentInput;
import com.projectguard.backend.risk.RiskRule;
import com.projectguard.backend.risk.RiskSeverity;
import com.projectguard.backend.risk.RiskSignal;
import com.projectguard.backend.risk.RiskSource;
import com.projectguard.backend.risk.RiskThresholds;
import com.projectguard.backend.risk.SmallDepositThresholds;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * 다가구주택에서 다른 방 세입자가 소액임차인이면, 나보다 나중에 들어왔어도 경매 시 최우선변제금을 먼저 받는다
 * (주택임대차보호법 제8조). 은행이 다가구 대출 때 "방 수 × 최우선변제금"을 미리 빼는 것과 같은 방식으로,
 * 다른 방이 모두 소액임차인으로 채워지는 최악의 경우를 계산해 보여준다.
 *
 *  - 최우선변제 총액은 주택가액의 2분의 1을 넘을 수 없다(시행령 제10조 제3항) → 기준 가격의 절반으로 상한.
 *  - 적용 금액은 원래 이 건물 최선순위 담보권 설정일 당시 기준이다. 여기서는 현행 기준을 써서, 오래된 근저당이
 *    있는 건물이면 실제보다 크게(보수적으로) 계산된다.
 *  - 먼저 들어온 세입자 보증금(입력값)과 일부 겹칠 수 있는 최악의 경우 가정이라 최고 CAUTION까지만 낸다.
 *
 * 방 수는 사용자가 입력한 값을 우선하고, 없으면 건축물대장 가구수를 쓴다. 둘 다 없으면 평가하지 않는다.
 */
@Component
public class MultiHouseholdSmallTenantRule implements RiskRule {

    @Override
    public Optional<RiskSignal> evaluate(RiskAssessmentInput input) {
        if (!input.isMultiHousehold() || input.registry().address() == null) {
            return Optional.empty();
        }
        BuildingInfo building = input.buildingInfo();
        Integer registeredRooms = building != null ? building.familyCount() : null;
        Integer rooms = input.roomCount() != null && input.roomCount() > 0 ? input.roomCount() : registeredRooms;
        if (rooms == null || rooms <= 1) {
            return Optional.empty();
        }

        Region region = RegionClassifier.classify(input.registry().address());
        SmallDepositThresholds.Bracket bracket = SmallDepositThresholds.forRegion(region);
        int otherRooms = rooms - 1;
        long potential = otherRooms * bracket.priorityAmount();

        MultiHouseholdPriceBasis basis = MultiHouseholdPriceBasis.of(input);
        if (basis != null) {
            potential = Math.min(potential, basis.price() / 2);
        }

        StringBuilder detail = new StringBuilder(String.format(
                "이 건물은 방이 %d개예요%s. 다른 방 %d곳에 보증금 %,d원 이하 소액임차인이 들어오면, 나보다 나중에 들어왔더라도 "
                        + "경매 때 한 곳당 최대 %,d원을 먼저 받아가요. 최악의 경우 이 금액이 %,d원%s이에요. ",
                rooms, roomsSourceNote(input.roomCount(), registeredRooms), otherRooms, bracket.depositCap(),
                bracket.priorityAmount(), potential, basis != null ? "(건물값의 절반 한도 적용)" : ""));

        if (input.roomCount() != null && registeredRooms != null && input.roomCount() > registeredRooms) {
            detail.append(String.format("입력하신 방 수가 건축물대장 가구수(%d가구)보다 많아요. 허가 없이 방을 나눈 "
                    + "'방 쪼개기'일 수 있고, 이 경우 위반건축물이 되거나 소액임차인이 더 늘어날 수 있어요. ", registeredRooms));
        }

        RiskSeverity severity = RiskSeverity.INFO;
        String title = "나중에 들어올 소액임차인도 먼저 받아갈 수 있어요";
        if (basis != null) {
            long prior = input.priorDepositTotal() != null ? input.priorDepositTotal() : 0L;
            long combined = MultiHouseholdPriceBasis.seniorMortgage(input) + prior + potential + input.depositAmount();
            double ratio = (double) combined / basis.price();
            detail.append(String.format(
                    "근저당·먼저 들어온 세입자 보증금%s·이 최우선변제금·내 보증금을 모두 더하면 %,d원으로 %s의 %.1f%%예요. ",
                    input.priorDepositTotal() != null ? "(입력값)" : "(입력 안 해 0원으로 계산)",
                    combined, basis.label(), ratio * 100));
            if (ratio >= RiskThresholds.SENIOR_DEBT_PLUS_DEPOSIT_RATIO_HIGH) {
                severity = RiskSeverity.CAUTION;
                title = "나중에 들어올 소액임차인까지 고려하면 보증금이 위험할 수 있어요";
            }
        }

        if (input.depositAmount() <= bracket.depositCap()) {
            detail.append(String.format("내 보증금도 소액임차인 기준 안이라 최대 %,d원까지는 먼저 받을 수 있지만, "
                    + "다른 소액임차인들과 건물값의 절반 안에서 나눠 가져요. ", bracket.priorityAmount()));
        }
        detail.append("실제 적용 금액은 이 건물에 가장 먼저 설정된 근저당 날짜의 기준을 따라서, 근저당이 오래됐다면 "
                + "이 계산보다 작을 수 있어요. 먼저 들어온 세입자와 겹칠 수 있는 최악의 경우 가정이에요.");

        return Optional.of(new RiskSignal(
                "MULTI_HOUSEHOLD_SMALL_TENANT_PRIORITY",
                title,
                severity,
                RiskSource.LAW,
                "주택임대차보호법 제8조, 시행령 제10·11조 (현행 기준·지역은 주소 기반 추정, 최악의 경우 가정)",
                detail.toString().trim()));
    }

    private String roomsSourceNote(Integer enteredRooms, Integer registeredRooms) {
        if (enteredRooms != null && enteredRooms > 0) {
            return "(입력값)";
        }
        return registeredRooms != null ? "(건축물대장 가구수)" : "";
    }
}
