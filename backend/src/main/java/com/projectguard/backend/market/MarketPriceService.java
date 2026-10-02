package com.projectguard.backend.market;

import com.projectguard.backend.common.PropertyType;
import org.springframework.stereotype.Service;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 사용자가 입력한 주소/건물명으로 최근 2개월 실거래가를 조회해 시세를 추정한다.
 * 주소를 법정동코드로 바꾸는 데 juso.go.kr 주소검색 API를, 실거래 내역 조회에
 * 부동산 유형별 국토교통부 실거래자료 API를 쓴다. 매칭되는 거래가 없으면 empty를 반환하고,
 * 이 경우 위험 판단 규칙 중 시세 비교가 필요한 규칙(전세가율 등)은 평가를 건너뛴다.
 */
@Service
public class MarketPriceService {

    private final JusoAddressClient jusoAddressClient;
    private final AptTradeClient aptTradeClient;
    private final OfficetelTradeClient officetelTradeClient;
    private final VillaTradeClient villaTradeClient;

    public MarketPriceService(
            JusoAddressClient jusoAddressClient,
            AptTradeClient aptTradeClient,
            OfficetelTradeClient officetelTradeClient,
            VillaTradeClient villaTradeClient
    ) {
        this.jusoAddressClient = jusoAddressClient;
        this.aptTradeClient = aptTradeClient;
        this.officetelTradeClient = officetelTradeClient;
        this.villaTradeClient = villaTradeClient;
    }

    public Optional<Long> lookupMarketPrice(
            PropertyType propertyType, String address, String buildingName, Double exclusiveAreaSqm
    ) {
        // 다가구주택 실거래가 API는 지번을 공개하지 않아 어느 건물 거래인지 알 수 없다 — 이웃 건물 가격을
        // 이 건물 시세로 추정하면 근거 없는 "안전" 판정이 나올 수 있어 자동 조회하지 않는다.
        if (propertyType == PropertyType.MULTI_HOUSEHOLD) {
            return Optional.empty();
        }

        Optional<JusoAddressResult> addressResult = jusoAddressClient.search(address);
        if (addressResult.isEmpty()) {
            return Optional.empty();
        }
        String lawdCd = addressResult.get().lawdCd();
        if (lawdCd == null) {
            return Optional.empty();
        }

        TradeClient tradeClient = tradeClientFor(propertyType);
        List<TradeRecord> records = new ArrayList<>();
        YearMonth now = YearMonth.now();
        records.addAll(tradeClient.fetchTrades(lawdCd, yyyyMM(now)));
        records.addAll(tradeClient.fetchTrades(lawdCd, yyyyMM(now.minusMonths(1))));

        String nameToMatch = (buildingName != null && !buildingName.isBlank())
                ? buildingName
                : addressResult.get().bdNm();

        return MarketPriceMatcher.match(records, nameToMatch, exclusiveAreaSqm);
    }

    private TradeClient tradeClientFor(PropertyType propertyType) {
        return switch (propertyType) {
            case APARTMENT -> aptTradeClient;
            case OFFICETEL -> officetelTradeClient;
            case VILLA -> villaTradeClient;
            case MULTI_HOUSEHOLD -> throw new IllegalArgumentException("다가구주택은 시세를 자동 조회하지 않는다.");
        };
    }

    private String yyyyMM(YearMonth ym) {
        return String.format("%04d%02d", ym.getYear(), ym.getMonthValue());
    }
}
