package com.projectguard.backend.market;

import java.util.List;

/**
 * 국토교통부 실거래자료 API 클라이언트 공통 인터페이스. 아파트/오피스텔/연립다세대 API는
 * 오퍼레이션 경로와 건물명 응답 필드만 다르고 나머지 호출 방식은 동일하다.
 */
public interface TradeClient {

    /**
     * @param lawdCd        5자리 시군구코드
     * @param dealYearMonth "yyyyMM" 형식 계약월
     */
    List<TradeRecord> fetchTrades(String lawdCd, String dealYearMonth);
}
