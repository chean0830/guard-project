package com.projectguard.backend.market;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * 국토교통부 연립다세대 매매 실거래자료 API.
 */
@Component
public class VillaTradeClient extends AbstractTradeClient {

    public VillaTradeClient(
            RestClient.Builder restClientBuilder,
            TradeResponseParser parser,
            @Value("${MOLIT_VILLA_TRADE_ENDPOINT:}") String endpoint,
            @Value("${DATA_GO_KR_API_KEY:}") String apiKey
    ) {
        super(restClientBuilder.build(), parser, endpoint, "getRTMSDataSvcRHTrade", apiKey, "mhouseNm");
    }
}
