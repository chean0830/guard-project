package com.projectguard.backend.market;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Optional;

/**
 * juso.go.kr 주소검색 API. 도로명주소 문자열로 법정동코드(admCd)를 얻는 용도로 쓴다.
 * 얻은 admCd의 앞 5자리가 실거래가 API 호출에 필요한 LAWD_CD다.
 */
@Component
public class JusoAddressClient {

    private final RestClient restClient;
    private final JusoAddressResponseParser parser;
    private final String apiKey;

    public JusoAddressClient(
            RestClient.Builder restClientBuilder,
            JusoAddressResponseParser parser,
            @Value("${JUSO_ADDRESS_API_KEY:}") String apiKey
    ) {
        this.restClient = restClientBuilder.build();
        this.parser = parser;
        this.apiKey = apiKey;
    }

    public Optional<JusoAddressResult> search(String keyword) {
        String body;
        try {
            body = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .scheme("https").host("www.juso.go.kr").path("/addrlink/addrLinkApi.do")
                            .queryParam("confmKey", apiKey)
                            .queryParam("currentPage", 1)
                            .queryParam("countPerPage", 1)
                            .queryParam("keyword", keyword)
                            .queryParam("resultType", "json")
                            .build())
                    .retrieve()
                    .body(String.class);
        } catch (RestClientException e) {
            return Optional.empty();
        }

        return parser.parseFirst(body);
    }
}
