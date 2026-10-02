package com.projectguard.backend.registry;

import java.util.List;

/**
 * @param registryKind 등기부 종류 (집합건물/건물/토지). 사용자가 고른 부동산 유형과 대조하는 데 쓴다.
 */
public record RegistryAnalysis(
        String address,
        String uniqueNumber,
        List<OwnershipEntry> ownershipHistory,
        List<MortgageEntry> mortgages,
        List<SeizureEntry> seizures,
        long totalActiveMortgageAmount,
        RegistryKind registryKind
) {

    /** 등기부 종류를 따지지 않는 곳(테스트 등)에서 쓰는 생성자. */
    public RegistryAnalysis(
            String address,
            String uniqueNumber,
            List<OwnershipEntry> ownershipHistory,
            List<MortgageEntry> mortgages,
            List<SeizureEntry> seizures,
            long totalActiveMortgageAmount
    ) {
        this(address, uniqueNumber, ownershipHistory, mortgages, seizures, totalActiveMortgageAmount,
                RegistryKind.UNKNOWN);
    }

    /** 현재 소유자 이름 (말소되지 않은 마지막 소유권 항목). 알 수 없으면 null. */
    public String currentOwnerName() {
        for (int i = ownershipHistory.size() - 1; i >= 0; i--) {
            OwnershipEntry entry = ownershipHistory.get(i);
            if (!entry.cancelled() && entry.ownerName() != null && !entry.ownerName().isBlank()) {
                return entry.ownerName().trim();
            }
        }
        return null;
    }
}
