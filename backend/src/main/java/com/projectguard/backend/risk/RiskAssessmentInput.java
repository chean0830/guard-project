package com.projectguard.backend.risk;

import com.projectguard.backend.common.ContractType;
import com.projectguard.backend.common.PriorDepositSource;
import com.projectguard.backend.common.PropertyType;
import com.projectguard.backend.common.ViolationBuildingAnswer;
import com.projectguard.backend.market.BuildingInfo;
import com.projectguard.backend.market.OfficialHousePrice;
import com.projectguard.backend.registry.RegistryAnalysis;

/**
 * @param registry        등기부 파싱 결과
 * @param contractType    계약 형태 (전세/월세). 전세가율처럼 보증금만 보는 규칙은 월세에 그대로
 *                        적용할 수 없어 규칙 평가 시 이 값으로 분기한다.
 * @param depositAmount   사용자가 입력한 보증금 (원)
 * @param monthlyRent     월세 (원). 전세면 null. 월세를 보증금으로 환산할 때 쓴다.
 * @param marketPrice     시세 (원). 집합건물은 실거래가 API로 조회한 호실 시세, 다가구주택은 사용자가
 *                        입력한 건물 전체 시세. 없으면 null — 시세 비교가 필요한 규칙은 평가를 건너뛴다.
 * @param declaredLandlordName 사용자가 입력한 임대인 이름 (선택). 등기부상 소유자와 대조하는 데 사용.
 * @param declaredAddress 사용자가 입력한 계약서상 주소 (선택). 등기부 주소와 대조하는 데 사용.
 * @param propertyType    부동산 유형. 다가구주택은 호실 단위 규칙(전세가율 등) 대신 전용 규칙으로 판단한다.
 * @param priorDepositTotal  다가구주택에서 나보다 먼저 들어온 세입자 보증금 합계 (원, 사용자 입력). 모르면 null.
 * @param priorDepositSource 위 금액을 어디서 확인했는지. 다가구주택이 아니면 null.
 * @param buildingInfo    건축물대장 조회 결과. 조회 실패 시 null — 건축물대장이 필요한 규칙은 평가를 건너뛴다.
 * @param violationBuilding 사용자가 건축물대장에서 확인한 위반건축물 표시 여부. 입력 안 했으면 null.
 * @param officialHousePrice 단독·다가구 공시가격. 다가구에서 건물 시세를 입력하지 않았을 때 기준값으로 쓴다. 없으면 null.
 * @param landRegistry    함께 올린 토지 등기부 파싱 결과 (다가구 선택 입력). 없으면 null.
 * @param roomCount       사용자가 입력한 다가구 건물 전체 방(호실) 수. 없으면 건축물대장 가구수를 쓴다.
 */
public record RiskAssessmentInput(
        RegistryAnalysis registry,
        ContractType contractType,
        long depositAmount,
        Long monthlyRent,
        Long marketPrice,
        String declaredLandlordName,
        String declaredAddress,
        PropertyType propertyType,
        Long priorDepositTotal,
        PriorDepositSource priorDepositSource,
        BuildingInfo buildingInfo,
        ViolationBuildingAnswer violationBuilding,
        OfficialHousePrice officialHousePrice,
        RegistryAnalysis landRegistry,
        Integer roomCount
) {

    /** 방 수 없이 만드는 입력. */
    public RiskAssessmentInput(
            RegistryAnalysis registry,
            ContractType contractType,
            long depositAmount,
            Long monthlyRent,
            Long marketPrice,
            String declaredLandlordName,
            String declaredAddress,
            PropertyType propertyType,
            Long priorDepositTotal,
            PriorDepositSource priorDepositSource,
            BuildingInfo buildingInfo,
            ViolationBuildingAnswer violationBuilding,
            OfficialHousePrice officialHousePrice,
            RegistryAnalysis landRegistry
    ) {
        this(registry, contractType, depositAmount, monthlyRent, marketPrice, declaredLandlordName, declaredAddress,
                propertyType, priorDepositTotal, priorDepositSource, buildingInfo, violationBuilding,
                officialHousePrice, landRegistry, null);
    }

    /** 토지 등기부 없이 만드는 입력. */
    public RiskAssessmentInput(
            RegistryAnalysis registry,
            ContractType contractType,
            long depositAmount,
            Long monthlyRent,
            Long marketPrice,
            String declaredLandlordName,
            String declaredAddress,
            PropertyType propertyType,
            Long priorDepositTotal,
            PriorDepositSource priorDepositSource,
            BuildingInfo buildingInfo,
            ViolationBuildingAnswer violationBuilding,
            OfficialHousePrice officialHousePrice
    ) {
        this(registry, contractType, depositAmount, monthlyRent, marketPrice, declaredLandlordName, declaredAddress,
                propertyType, priorDepositTotal, priorDepositSource, buildingInfo, violationBuilding,
                officialHousePrice, null);
    }

    /** 공시가격 없이 만드는 입력. */
    public RiskAssessmentInput(
            RegistryAnalysis registry,
            ContractType contractType,
            long depositAmount,
            Long monthlyRent,
            Long marketPrice,
            String declaredLandlordName,
            String declaredAddress,
            PropertyType propertyType,
            Long priorDepositTotal,
            PriorDepositSource priorDepositSource,
            BuildingInfo buildingInfo,
            ViolationBuildingAnswer violationBuilding
    ) {
        this(registry, contractType, depositAmount, monthlyRent, marketPrice, declaredLandlordName, declaredAddress,
                propertyType, priorDepositTotal, priorDepositSource, buildingInfo, violationBuilding, null);
    }

    /** 위반건축물 확인값 없이 만드는 입력. */
    public RiskAssessmentInput(
            RegistryAnalysis registry,
            ContractType contractType,
            long depositAmount,
            Long monthlyRent,
            Long marketPrice,
            String declaredLandlordName,
            String declaredAddress,
            PropertyType propertyType,
            Long priorDepositTotal,
            PriorDepositSource priorDepositSource,
            BuildingInfo buildingInfo
    ) {
        this(registry, contractType, depositAmount, monthlyRent, marketPrice, declaredLandlordName, declaredAddress,
                propertyType, priorDepositTotal, priorDepositSource, buildingInfo, null);
    }

    /** 건축물대장 정보 없이 만드는 입력. */
    public RiskAssessmentInput(
            RegistryAnalysis registry,
            ContractType contractType,
            long depositAmount,
            Long monthlyRent,
            Long marketPrice,
            String declaredLandlordName,
            String declaredAddress,
            PropertyType propertyType,
            Long priorDepositTotal,
            PriorDepositSource priorDepositSource
    ) {
        this(registry, contractType, depositAmount, monthlyRent, marketPrice, declaredLandlordName, declaredAddress,
                propertyType, priorDepositTotal, priorDepositSource, null);
    }

    /** 집합건물(아파트 등) 입력. 다가구 전용 값은 비워 둔다. */
    public RiskAssessmentInput(
            RegistryAnalysis registry,
            ContractType contractType,
            long depositAmount,
            Long monthlyRent,
            Long marketPrice,
            String declaredLandlordName,
            String declaredAddress
    ) {
        this(registry, contractType, depositAmount, monthlyRent, marketPrice, declaredLandlordName, declaredAddress,
                PropertyType.APARTMENT, null, null, null);
    }

    public boolean isMultiHousehold() {
        return propertyType == PropertyType.MULTI_HOUSEHOLD;
    }
}
