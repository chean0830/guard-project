package com.projectguard.backend.market;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * 국토교통부 건축HUB 건축물대장정보 서비스(BldRgstHubService) 표제부(getBrTitleInfo) 응답을 파싱한다.
 */
@Component
public class BuildingRegisterResponseParser {

    private final ObjectMapper objectMapper;

    public BuildingRegisterResponseParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Optional<BuildingInfo> parseFirst(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode items = root.path("response").path("body").path("items").path("item");
            JsonNode first;
            if (items.isArray()) {
                if (items.isEmpty()) {
                    return Optional.empty();
                }
                first = items.get(0);
            } else if (items.isObject()) {
                first = items;
            } else {
                return Optional.empty();
            }

            return Optional.of(new BuildingInfo(
                    first.path("bldNm").asText(null),
                    first.path("mainPurpsCdNm").asText(null),
                    first.path("strctCdNm").asText(null),
                    first.path("useAprDay").asText(null),
                    parseArea(first.path("totArea").asText(null))
            ));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private Double parseArea(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
