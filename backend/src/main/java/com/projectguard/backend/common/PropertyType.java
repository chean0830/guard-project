package com.projectguard.backend.common;

/**
 * 지원 대상 부동산 유형. 아파트/오피스텔/빌라는 "집합건물" 등기부라 파싱 로직이 같고,
 * 실거래가 조회는 국토교통부 API가 유형별로 나뉘어 있어 이 값으로 API를 선택한다.
 *
 * 다가구주택(원룸 건물 대부분)은 건물 전체에 등기부가 하나뿐이라, 나보다 먼저 들어온 세입자들의
 * 보증금이 등기부에 나오지 않는다. 그래서 시세를 자동 조회하지 않고, 선순위 보증금·건물 시세를
 * 사용자가 직접 입력한 값으로만 계산하며 "안전" 판정은 내리지 않는다 (docs/결정사항.md 참고).
 */
public enum PropertyType {
    APARTMENT,
    OFFICETEL,
    VILLA,
    MULTI_HOUSEHOLD
}
