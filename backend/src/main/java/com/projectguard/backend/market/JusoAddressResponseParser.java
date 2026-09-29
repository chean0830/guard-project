package com.projectguard.backend.market;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class JusoAddressResponseParser {

    private final ObjectMapper objectMapper;

    public JusoAddressResponseParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Optional<JusoAddressResult> parseFirst(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode jusoArray = root.path("results").path("juso");
            if (!jusoArray.isArray() || jusoArray.isEmpty()) {
                return Optional.empty();
            }
            JsonNode first = jusoArray.get(0);
            return Optional.of(new JusoAddressResult(
                    first.path("roadAddr").asText(null),
                    first.path("admCd").asText(null),
                    first.path("bdNm").asText(null),
                    first.path("mtYn").asText(null),
                    first.path("lnbrMnnm").asText(null),
                    first.path("lnbrSlno").asText(null)
            ));
        } catch (Exception e) {
            return Optional.empty();
        }
    }
}
