package com.projectguard.backend.market;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 국토교통부 건축HUB 건축물대장정보 서비스(BldRgstHubService) 표제부(getBrTitleInfo)·층별개요(getBrFlrOulnInfo)
 * 응답을 파싱한다. 결과가 한 건이면 item이 배열이 아니라 객체로 온다.
 */
@Component
public class BuildingRegisterResponseParser {

    private final ObjectMapper objectMapper;

    public BuildingRegisterResponseParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Optional<BuildingInfo> parseFirst(String responseBody) {
        try {
            List<JsonNode> items = items(responseBody);
            if (items.isEmpty()) {
                return Optional.empty();
            }
            JsonNode first = items.get(0);

            return Optional.of(new BuildingInfo(
                    first.path("bldNm").asText(null),
                    first.path("mainPurpsCdNm").asText(null),
                    first.path("strctCdNm").asText(null),
                    first.path("useAprDay").asText(null),
                    parseArea(first.path("totArea").asText(null)),
                    blankToNull(first.path("mainPurpsCd").asText(null)),
                    blankToNull(first.path("etcPurps").asText(null)),
                    List.of(),
                    positiveOrNull(first.path("fmlyCnt").asInt(0))
            ));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public List<BuildingInfo.FloorUse> parseFloors(String responseBody) {
        try {
            List<BuildingInfo.FloorUse> floors = new ArrayList<>();
            for (JsonNode item : items(responseBody)) {
                floors.add(new BuildingInfo.FloorUse(
                        "10".equals(item.path("flrGbCd").asText(null)),
                        item.path("flrNo").asInt(0),
                        blankToNull(item.path("flrNoNm").asText(null)),
                        blankToNull(item.path("mainPurpsCd").asText(null)),
                        blankToNull(item.path("mainPurpsCdNm").asText(null)),
                        blankToNull(item.path("etcPurps").asText(null))));
            }
            return floors;
        } catch (Exception e) {
            return List.of();
        }
    }

    private List<JsonNode> items(String responseBody) {
        JsonNode items = objectMapper.readTree(responseBody).path("response").path("body").path("items").path("item");
        List<JsonNode> result = new ArrayList<>();
        if (items.isArray()) {
            items.forEach(result::add);
        } else if (items.isObject()) {
            result.add(items);
        }
        return result;
    }

    private Integer positiveOrNull(int value) {
        return value > 0 ? value : null;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
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
