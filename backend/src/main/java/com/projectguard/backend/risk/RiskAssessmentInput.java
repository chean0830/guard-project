package com.projectguard.backend.risk;

import com.projectguard.backend.common.ContractType;
import com.projectguard.backend.registry.RegistryAnalysis;

/**
 * @param registry        등기부 파싱 결과
 * @param contractType    계약 형태 (전세/월세). 전세가율처럼 보증금만 보는 규칙은 월세에 그대로
 *                        적용할 수 없어 규칙 평가 시 이 값으로 분기한다.
 * @param depositAmount   사용자가 입력한 보증금 (원)
 * @param monthlyRent     월세 (원). 전세면 null. 월세를 보증금으로 환산할 때 쓴다.
 * @param marketPrice     실거래가 API로 조회한 시세 (원). 아직 조회 전이면 null — 시세 비교가 필요한
 *                        규칙(전세가율 등)은 이때 평가를 건너뛴다.
 * @param declaredLandlordName 사용자가 입력한 임대인 이름 (선택). 등기부상 소유자와 대조하는 데 사용.
 * @param declaredAddress 사용자가 입력한 계약서상 주소 (선택). 등기부 주소와 대조하는 데 사용.
 */
public record RiskAssessmentInput(
        RegistryAnalysis registry,
        ContractType contractType,
        long depositAmount,
        Long monthlyRent,
        Long marketPrice,
        String declaredLandlordName,
        String declaredAddress
) {
}
