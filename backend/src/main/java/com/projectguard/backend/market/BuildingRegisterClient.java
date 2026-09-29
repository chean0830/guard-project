package com.projectguard.backend.market;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.URI;
import java.util.Optional;

/**
 * 국토교통부 건축HUB 건축물대장정보 서비스(BldRgstHubService) 표제부(getBrTitleInfo) API.
 * 지번(시군구코드+법정동코드+대지구분코드+본번+부번) 기준으로 조회하며, 지번 정보는
 * juso.go.kr 주소검색 결과(JusoAddressResult)에서 얻는다.
 */
@Component
public class BuildingRegisterClient {

    private final RestClient restClient;
    private final BuildingRegisterResponseParser parser;
    private final String endpoint;
    private final String apiKey;

    public BuildingRegisterClient(
            RestClient.Builder restClientBuilder,
            BuildingRegisterResponseParser parser,
            @Value("${BUILDING_REGISTER_ENDPOINT:}") String endpoint,
            @Value("${DATA_GO_KR_API_KEY:}") String apiKey
    ) {
        this.restClient = restClientBuilder.build();
        this.parser = parser;
        this.endpoint = endpoint;
        this.apiKey = apiKey;
    }

    public Optional<BuildingInfo> fetchTitleInfo(JusoAddressResult address) {
        String url = endpoint + "/getBrTitleInfo";
        String body;
        try {
            body = restClient.get()
                    .uri(uriBuilder -> {
                        URI uri = URI.create(url);
                        return uriBuilder
                                .scheme(uri.getScheme()).host(uri.getHost()).path(uri.getPath())
                                .queryParam("serviceKey", apiKey)
                                .queryParam("sigunguCd", address.sigunguCd())
                                .queryParam("bjdongCd", address.bjdongCd())
                                .queryParam("platGbCd", address.platGbCd())
                                .queryParam("bun", address.bun())
                                .queryParam("ji", address.ji())
                                .queryParam("numOfRows", 5)
                                .queryParam("_type", "json")
                                .build();
                    })
                    .retrieve()
                    .body(String.class);
        } catch (RestClientException e) {
            // 건축HUB API가 일시적으로 응답하지 않아도 건축물대장 정보는 "조회 실패"로 건너뛸 뿐,
            // 등기부 분석 자체가 실패해서는 안 된다 (실제로 503 SERVICETIMEOUT_ERROR가 발생함을 확인).
            return Optional.empty();
        }

        return parser.parseFirst(body);
    }
}
