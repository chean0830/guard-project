package com.projectguard.backend.market;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * 국토교통부 오피스텔 매매 실거래자료 API.
 */
@Component
public class OfficetelTradeClient extends AbstractTradeClient {

    public OfficetelTradeClient(
            RestClient.Builder restClientBuilder,
            TradeResponseParser parser,
            @Value("${MOLIT_OFFICETEL_TRADE_ENDPOINT:}") String endpoint,
            @Value("${DATA_GO_KR_API_KEY:}") String apiKey
    ) {
        super(restClientBuilder.build(), parser, endpoint, "getRTMSDataSvcOffiTrade", apiKey, "offiNm");
    }
}
