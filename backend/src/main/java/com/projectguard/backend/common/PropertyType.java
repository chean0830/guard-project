package com.projectguard.backend.common;

/**
 * 지원 대상 부동산 유형. 등기부 파싱 로직은 세 유형 모두 "집합건물" 형식으로 동일하지만,
 * 실거래가 조회는 국토교통부 API가 유형별로 나뉘어 있어 이 값으로 API를 선택한다.
 * 원룸/다가구주택은 범위 밖 (docs/결정사항.md 7번 참고).
 */
public enum PropertyType {
    APARTMENT,
    OFFICETEL,
    VILLA
}
