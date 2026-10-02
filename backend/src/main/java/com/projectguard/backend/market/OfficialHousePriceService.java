package com.projectguard.backend.market;

import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * 등기부상 주소로 단독·다가구주택 공시가격(개별주택가격)을 조회한다. 주소를 juso.go.kr로 지번으로 바꾼 뒤
 * 필지고유번호(PNU)를 만들어 브이월드 API를 호출한다. 다가구는 실거래가로 이 건물 시세를 찾을 수 없어
 * (MarketPriceService 참고), 사용자가 시세를 입력하지 않았을 때 공시가격을 보수적인 기준값으로 쓴다.
 */
@Service
public class OfficialHousePriceService {

    private final JusoAddressClient jusoAddressClient;
    private final VworldHousingPriceClient vworldHousingPriceClient;

    public OfficialHousePriceService(
            JusoAddressClient jusoAddressClient, VworldHousingPriceClient vworldHousingPriceClient
    ) {
        this.jusoAddressClient = jusoAddressClient;
        this.vworldHousingPriceClient = vworldHousingPriceClient;
    }

    public Optional<OfficialHousePrice> lookup(String address) {
        if (address == null || address.isBlank()) {
            return Optional.empty();
        }
        return jusoAddressClient.search(address)
                .map(JusoAddressResult::pnu)
                .flatMap(vworldHousingPriceClient::fetchLatest);
    }
}
