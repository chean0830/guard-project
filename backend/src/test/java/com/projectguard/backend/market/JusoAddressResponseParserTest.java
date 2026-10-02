package com.projectguard.backend.market;

import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JusoAddressResponseParserTest {

    private final JusoAddressResponseParser parser = new JusoAddressResponseParser(new ObjectMapper());

    private String loadFixture() throws IOException {
        try (var is = getClass().getResourceAsStream("/market/juso-response-sample.json")) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void 첫번째_주소결과를_파싱하고_법정동코드_앞5자리를_LAWD_CD로_추출한다() throws IOException {
        Optional<JusoAddressResult> result = parser.parseFirst(loadFixture());

        assertTrue(result.isPresent());
        assertEquals("1271033530", result.get().admCd());
        assertEquals("12710", result.get().lawdCd());
    }

    @Test
    void 건축물대장_조회에_필요한_지번_정보도_함께_파싱한다() throws IOException {
        Optional<JusoAddressResult> result = parser.parseFirst(loadFixture());

        assertTrue(result.isPresent());
        assertEquals("12710", result.get().sigunguCd());
        assertEquals("33530", result.get().bjdongCd());
        assertEquals("0", result.get().platGbCd());
        assertEquals("0476", result.get().bun());
        assertEquals("0000", result.get().ji());
    }

    @Test
    void 결과가_없으면_empty를_반환한다() {
        String emptyResponse = "{\"results\":{\"common\":{\"totalCount\":\"0\"},\"juso\":[]}}";
        assertTrue(parser.parseFirst(emptyResponse).isEmpty());
    }

    @Test
    void 필지고유번호는_대지구분을_1_산을_2로_만든다() {
        assertEquals("1162010100100120003",
                new JusoAddressResult(null, "1162010100", null, "0", "12", "3").pnu());
        assertEquals("1162010100200120000",
                new JusoAddressResult(null, "1162010100", null, "1", "12", "0").pnu());
        assertEquals(null, new JusoAddressResult(null, null, null, "0", "12", "3").pnu());
    }
}
