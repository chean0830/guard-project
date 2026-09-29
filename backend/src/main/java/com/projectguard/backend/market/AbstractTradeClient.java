package com.projectguard.backend.market;

import org.springframework.web.client.RestClient;

import java.net.URI;
import java.util.List;

/**
 * 아파트/오피스텔/연립다세대 실거래자료 API는 오퍼레이션 경로와 건물명 응답 필드만 다르고
 * 나머지 호출 방식(쿼리 파라미터, 인증키)이 동일해 공통 로직을 여기에 둔다.
 * data.go.kr 상세 페이지의 End Point는 서비스명까지만 표시하고, 실제 호출에는
 * 오퍼레이션 경로가 하나 더 필요하다 (실제 호출로 확인, docs/결정사항.md 참고).
 */
abstract class AbstractTradeClient implements TradeClient {

    private final RestClient restClient;
    private final TradeResponseParser parser;
    private final String endpoint;
    private final String operation;
    private final String apiKey;
    private final String buildingNameField;

    protected AbstractTradeClient(
            RestClient restClient,
            TradeResponseParser parser,
            String endpoint,
            String operation,
            String apiKey,
            String buildingNameField
    ) {
        this.restClient = restClient;
        this.parser = parser;
        this.endpoint = endpoint;
        this.operation = operation;
        this.apiKey = apiKey;
        this.buildingNameField = buildingNameField;
    }

    @Override
    public List<TradeRecord> fetchTrades(String lawdCd, String dealYearMonth) {
        String url = endpoint + "/" + operation;
        String body = restClient.get()
                .uri(uriBuilder -> {
                    URI uri = URI.create(url);
                    return uriBuilder
                            .scheme(uri.getScheme()).host(uri.getHost()).path(uri.getPath())
                            .queryParam("serviceKey", apiKey)
                            .queryParam("LAWD_CD", lawdCd)
                            .queryParam("DEAL_YMD", dealYearMonth)
                            .queryParam("_type", "json")
                            .queryParam("numOfRows", 500)
                            .build();
                })
                .retrieve()
                .body(String.class);

        return parser.parse(body, buildingNameField);
    }
}
