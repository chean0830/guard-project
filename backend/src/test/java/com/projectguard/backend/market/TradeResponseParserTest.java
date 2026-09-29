package com.projectguard.backend.market;

import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TradeResponseParserTest {

    private final TradeResponseParser parser = new TradeResponseParser(new ObjectMapper());

    private String loadFixture() throws IOException {
        try (var is = getClass().getResourceAsStream("/market/apt-trade-response-sample.json")) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void 거래_목록을_파싱하고_만원단위를_원단위로_환산한다() throws IOException {
        List<TradeRecord> records = parser.parse(loadFixture(), "aptNm");

        assertEquals(3, records.size());

        TradeRecord first = records.get(0);
        assertEquals("한신(개포)", first.buildingName());
        assertEquals(2_840_000_000L, first.dealAmount()); // "284,000" 만원 -> 28억4천만원
        assertEquals(52.73, first.exclusiveAreaSqm());
        assertEquals(2025, first.dealYear());
        assertEquals(8, first.dealMonth());
        assertEquals(5, first.dealDay());
        assertEquals("도곡동", first.dongName());
    }

    @Test
    void 빈응답이나_잘못된_형식이면_빈목록을_반환한다() {
        assertEquals(0, parser.parse("{}", "aptNm").size());
        assertEquals(0, parser.parse("not a json", "aptNm").size());
    }
}
