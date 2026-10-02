package com.projectguard.backend.market;

/**
 * 개별주택가격(단독·다가구주택 공시가격).
 *
 * @param price 공시가격 (원)
 * @param year  기준연도 (매년 1월 1일 기준으로 공시)
 */
public record OfficialHousePrice(long price, int year) {
}
