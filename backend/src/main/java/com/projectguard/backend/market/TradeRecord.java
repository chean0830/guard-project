package com.projectguard.backend.market;

/**
 * 아파트/오피스텔/연립다세대 실거래가 API 응답 한 건을 나타낸다.
 * 세 API의 응답 구조가 동일해(건물명 필드명만 다름) 공통 타입으로 표현한다.
 */
public record TradeRecord(
        String buildingName,
        long dealAmount,
        double exclusiveAreaSqm,
        int dealYear,
        int dealMonth,
        int dealDay,
        int floor,
        String dongName
) {
}
