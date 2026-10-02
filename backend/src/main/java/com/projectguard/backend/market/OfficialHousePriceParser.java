package com.projectguard.backend.market;

import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 브이월드 개별주택가격속성조회(getIndvdHousingPriceAttr) 응답을 파싱한다.
 * 응답은 {"indvdHousingPrices": {"field": [연도별 기록...]}} 형태이고 가격 필드는 housePc(원)다.
 * 연도별로 여러 건이 와서 기준연도(stdrYear)가 가장 최근인 기록을 고른다.
 */
@Component
public class OfficialHousePriceParser {

    private final ObjectMapper objectMapper;

    public OfficialHousePriceParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Optional<OfficialHousePrice> parseLatest(String responseBody) {
        try {
            JsonNode fields = objectMapper.readTree(responseBody).path("indvdHousingPrices").path("field");
            List<JsonNode> records = new ArrayList<>();
            if (fields.isArray()) {
                fields.forEach(records::add);
            } else if (fields.isObject()) {
                records.add(fields);
            }

            OfficialHousePrice latest = null;
            for (JsonNode record : records) {
                long price = parseLong(record.path("housePc").asText(""));
                int year = (int) parseLong(record.path("stdrYear").asText(""));
                if (price <= 0 || year <= 0) {
                    continue;
                }
                if (latest == null || year > latest.year()) {
                    latest = new OfficialHousePrice(price, year);
                }
            }
            return Optional.ofNullable(latest);
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private long parseLong(String value) {
        try {
            return Long.parseLong(value.replace(",", "").trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
