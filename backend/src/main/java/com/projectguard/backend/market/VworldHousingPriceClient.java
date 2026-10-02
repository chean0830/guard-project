package com.projectguard.backend.market;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Optional;

/**
 * 브이월드(국가공간정보센터) 개별주택가격속성조회 API. 국토교통부 개별주택가격정보는 공공데이터포털에서
 * "LINK" 유형이라 실제 호출은 브이월드에서 하고, 인증키도 브이월드에서 따로 발급받는다
 * (발급 시 등록한 서비스 URL을 domain 파라미터로 같이 보내야 한다). 키가 없으면 조회하지 않는다.
 */
@Component
public class VworldHousingPriceClient {

    private static final String URL = "https://api.vworld.kr/ned/data/getIndvdHousingPriceAttr";

    private final RestClient restClient;
    private final OfficialHousePriceParser parser;
    private final String apiKey;
    private final String domain;

    public VworldHousingPriceClient(
            RestClient.Builder restClientBuilder,
            OfficialHousePriceParser parser,
            @Value("${VWORLD_API_KEY:}") String apiKey,
            @Value("${VWORLD_DOMAIN:localhost}") String domain
    ) {
        this.restClient = restClientBuilder.build();
        this.parser = parser;
        this.apiKey = apiKey;
        this.domain = domain;
    }

    public Optional<OfficialHousePrice> fetchLatest(String pnu) {
        if (apiKey == null || apiKey.isBlank() || pnu == null) {
            return Optional.empty();
        }
        String body;
        try {
            body = restClient.get()
                    .uri(URL + "?key={key}&domain={domain}&pnu={pnu}&format=json&numOfRows=100&pageNo=1",
                            apiKey, domain, pnu)
                    .retrieve()
                    .body(String.class);
        } catch (RestClientException e) {
            // 공시가격은 참고 정보라, 조회 실패가 분석 자체를 실패시키면 안 된다.
            return Optional.empty();
        }
        return parser.parseLatest(body);
    }
}
