package com.projectguard.backend.market;

/**
 * 건축물대장 표제부(getBrTitleInfo) 조회 결과 중 사용자에게 보여줄 핵심 정보.
 */
public record BuildingInfo(
        String buildingName,
        String mainPurpose,
        String structureType,
        String useApprovalDate,
        Double totalFloorAreaSqm
) {
}
