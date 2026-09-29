package com.projectguard.backend.market;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarketPriceMatcherTest {

    private final List<TradeRecord> records = List.of(
            new TradeRecord("테스트아파트", 500_000_000L, 84.9, 2025, 3, 10, 5, "테스트동"),
            new TradeRecord("테스트아파트", 520_000_000L, 84.9, 2025, 6, 1, 8, "테스트동"),
            new TradeRecord("다른아파트", 300_000_000L, 59.9, 2025, 6, 1, 3, "테스트동")
    );

    @Test
    void 같은_단지_최신_거래가를_반환한다() {
        Optional<Long> result = MarketPriceMatcher.match(records, "테스트아파트", 84.9);

        assertTrue(result.isPresent());
        assertEquals(520_000_000L, result.get()); // 3월 건보다 6월 건이 최신
    }

    @Test
    void 공백_차이는_무시하고_매칭한다() {
        Optional<Long> result = MarketPriceMatcher.match(records, "테스트 아파트", 84.9);
        assertTrue(result.isPresent());
    }

    @Test
    void 면적이_허용오차를_벗어나면_매칭하지_않는다() {
        Optional<Long> result = MarketPriceMatcher.match(records, "테스트아파트", 150.0);
        assertTrue(result.isEmpty());
    }

    @Test
    void 일치하는_단지가_없으면_empty() {
        assertTrue(MarketPriceMatcher.match(records, "없는아파트", null).isEmpty());
    }
}
