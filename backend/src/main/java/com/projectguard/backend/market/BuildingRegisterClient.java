package com.projectguard.backend.market;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.URI;
import java.util.List;
import java.util.Optional;

/**
 * 국토교통부 건축HUB 건축물대장정보 서비스(BldRgstHubService) 표제부(getBrTitleInfo)·층별개요(getBrFlrOulnInfo) API.
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
        String body = fetch("getBrTitleInfo", address, 5);
        return body == null ? Optional.empty() : parser.parseFirst(body);
    }

    /** 층별 용도. 근린생활시설로 등록된 층을 찾는 데 쓴다. 조회 실패 시 빈 목록. */
    public List<BuildingInfo.FloorUse> fetchFloors(JusoAddressResult address) {
        String body = fetch("getBrFlrOulnInfo", address, 100);
        return body == null ? List.of() : parser.parseFloors(body);
    }

    private String fetch(String operation, JusoAddressResult address, int numOfRows) {
        String url = endpoint + "/" + operation;
        try {
            return restClient.get()
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
                                // pageNo 없이 numOfRows만 보내면 무시되고 1건만 온다 (실제 호출로 확인)
                                .queryParam("pageNo", 1)
                                .queryParam("numOfRows", numOfRows)
                                .queryParam("_type", "json")
                                .build();
                    })
                    .retrieve()
                    .body(String.class);
        } catch (RestClientException e) {
            // 건축HUB API가 일시적으로 응답하지 않아도 건축물대장 정보는 "조회 실패"로 건너뛸 뿐,
            // 등기부 분석 자체가 실패해서는 안 된다 (실제로 503 SERVICETIMEOUT_ERROR가 발생함을 확인).
            return null;
        }
    }
}
