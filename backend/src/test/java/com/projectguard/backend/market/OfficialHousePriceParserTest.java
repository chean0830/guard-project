package com.projectguard.backend.market;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OfficialHousePriceParserTest {

    private final OfficialHousePriceParser parser = new OfficialHousePriceParser(new ObjectMapper());

    @Test
    void 연도별_기록_중_가장_최근_공시가격을_고른다() {
        String body = """
                {"indvdHousingPrices":{"field":[
                  {"pnu":"1162010100100010001","stdrYear":"2024","housePc":"812000000"},
                  {"pnu":"1162010100100010001","stdrYear":"2026","housePc":"905000000"},
                  {"pnu":"1162010100100010001","stdrYear":"2025","housePc":"870000000"}]}}
                """;

        OfficialHousePrice price = parser.parseLatest(body).orElseThrow();

        assertEquals(905_000_000L, price.price());
        assertEquals(2026, price.year());
    }

    @Test
    void 기록이_한_건이면_객체로_와도_읽는다() {
        String body = "{\"indvdHousingPrices\":{\"field\":{\"stdrYear\":\"2026\",\"housePc\":\"500000000\"}}}";

        assertEquals(500_000_000L, parser.parseLatest(body).orElseThrow().price());
    }

    @Test
    void 기록이_없거나_오류_응답이면_empty() {
        assertTrue(parser.parseLatest("{\"indvdHousingPrices\":{\"field\":[]}}").isEmpty());
        assertTrue(parser.parseLatest("{\"response\":{\"status\":\"ERROR\"}}").isEmpty());
        assertTrue(parser.parseLatest("not json").isEmpty());
    }
}
