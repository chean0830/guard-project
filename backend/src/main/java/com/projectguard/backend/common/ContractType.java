package com.projectguard.backend.common;

/**
 * 계약 형태. 전세는 보증금만, 월세는 보증금+월세를 낸다 — 전세가율처럼 보증금만 보는
 * 위험 판단 규칙은 월세에는 그대로 적용할 수 없어 규칙 평가 시 이 값으로 분기한다.
 */
public enum ContractType {
    JEONSE,
    WOLSE
}
