package com.projectguard.backend.market;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * 실거래가 목록에서 우리가 찾는 단지/면적에 해당하는 거래를 골라 최신 거래가를 시세로 추정한다.
 * 단지명은 공백 제거 후 서로 포함관계면 일치로 보고(표기 차이 흡수), 전용면적은 3㎡ 오차까지 허용한다.
 */
public final class MarketPriceMatcher {

    private static final double AREA_TOLERANCE_SQM = 3.0;

    public static Optional<Long> match(List<TradeRecord> records, String buildingName, Double targetAreaSqm) {
        if (records == null || records.isEmpty() || buildingName == null || buildingName.isBlank()) {
            return Optional.empty();
        }

        String normalizedTarget = normalize(buildingName);

        return records.stream()
                .filter(r -> r.buildingName() != null)
                .filter(r -> {
                    String normalizedName = normalize(r.buildingName());
                    return normalizedName.contains(normalizedTarget) || normalizedTarget.contains(normalizedName);
                })
                .filter(r -> targetAreaSqm == null
                        || Math.abs(r.exclusiveAreaSqm() - targetAreaSqm) <= AREA_TOLERANCE_SQM)
                .max(Comparator.comparing((TradeRecord r) -> r.dealYear())
                        .thenComparing(TradeRecord::dealMonth)
                        .thenComparing(TradeRecord::dealDay))
                .map(TradeRecord::dealAmount);
    }

    private static String normalize(String s) {
        return s.replaceAll("\\s+", "");
    }

    private MarketPriceMatcher() {
    }
}
