package com.projectguard.backend.registry;

import java.util.List;
import java.util.Objects;

/**
 * 다가구주택(단독주택)은 건물과 토지 등기부가 따로 있어, 토지에만 근저당·압류가 걸려 있을 수 있다.
 * 건물 등기부와 토지 등기부를 대조하는 도우미.
 */
public final class LandRegistryComparison {

    private LandRegistryComparison() {
    }

    /**
     * 토지 등기부의 말소되지 않은 근저당 중 건물 등기부에 없는 것. 건물과 토지를 함께 담보로 잡는
     * 공동담보 근저당은 양쪽 등기부에 같은 접수일·채권최고액·근저당권자로 올라가므로, 이 셋이 같으면
     * 같은 근저당으로 보고 한 번만 센다 (두 번 더하면 선순위 채권이 부풀려진다).
     */
    public static List<MortgageEntry> landOnlyActiveMortgages(RegistryAnalysis building, RegistryAnalysis land) {
        if (land == null) {
            return List.of();
        }
        List<MortgageEntry> buildingActive = building.mortgages().stream().filter(m -> !m.cancelled()).toList();
        return land.mortgages().stream()
                .filter(m -> !m.cancelled())
                .filter(m -> buildingActive.stream().noneMatch(b -> sameJointMortgage(b, m)))
                .toList();
    }

    public static long landOnlyActiveMortgageAmount(RegistryAnalysis building, RegistryAnalysis land) {
        return landOnlyActiveMortgages(building, land).stream().mapToLong(MortgageEntry::maxClaimAmount).sum();
    }

    private static boolean sameJointMortgage(MortgageEntry a, MortgageEntry b) {
        return a.maxClaimAmount() == b.maxClaimAmount()
                && Objects.equals(a.receivedDate(), b.receivedDate())
                && Objects.equals(normalize(a.mortgageeName()), normalize(b.mortgageeName()));
    }

    private static String normalize(String s) {
        return s == null ? null : s.replaceAll("\\s+", "");
    }
}
